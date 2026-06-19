package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 内容生成服务。
 *
 * <p>负责记录生成任务、调用 Spring AI {@link ChatClient}、
 * 写入用量记录,并把结构化 JSON 返回给前端。</p>
 */
@Service
public class GenerationService {
  private final String modelName;

  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final ChatClient chatClient;
  private final HtmlCardValidator validator;
  private final ObjectMapper objectMapper;

  public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ChatClient chatClient,
    HtmlCardValidator validator,
    ObjectMapper objectMapper,
    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}") String modelName
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.chatClient = chatClient;
    this.validator = validator;
    this.objectMapper = objectMapper;
    this.modelName = modelName;
  }

  public GenerateContentResponse generate(GenerateContentRequest request) {
    String taskId = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    String inputText = inputText(request);
    String paramsJson = toJson(Map.of(
      "generationMode", request.generationMode(),
      "outputFormat", request.outputFormat(),
      "renderMode", request.renderMode(),
      "templateId", request.templateId()
    ));

    jdbc.update("""
        insert into generate_task
        (id, user_id, project_id, task_type, input_text, input_params_json, status, error_message, started_at, finished_at, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        taskId,
        properties.app().defaultUserId(),
        null,
        "content_generation",
        inputText,
        paramsJson,
        "running",
        null,
        now,
        null,
        now
    );

    try {
      String contentJson = chatClient.prompt()
        .system(buildSystemPrompt())
        .user(buildUserPrompt(request))
        .call()
        .content();
      validator.validate(objectMapper.readTree(contentJson));
      String finishedAt = Instant.now().toString();
      jdbc.update("update generate_task set status = ?, finished_at = ? where id = ?", "succeeded", finishedAt, taskId);
      jdbc.update("""
          insert into usage_record (id, user_id, task_id, usage_type, amount, model_name, created_at)
          values (?, ?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID().toString(),
          properties.app().defaultUserId(),
          taskId,
          "content_generation",
          1,
          modelName,
          finishedAt
      );
      return new GenerateContentResponse(taskId, contentJson);
    } catch (Exception e) {
      String finishedAt = Instant.now().toString();
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      jdbc.update(
        "update generate_task set status = ?, error_message = ?, finished_at = ? where id = ?",
        "failed", message, finishedAt, taskId
      );
      throw new IllegalStateException(message, e);
    }
  }

  private String buildSystemPrompt() {
    return """
        你是 CardFlow AI 的信息卡片 HTML 设计师。

        ## 输出协议（必须严格遵守）
        返回 JSON 必须包含且只包含以下字段：
        {
          "kind": "html_card",
          "title": "作品标题",
          "html": "<!doctype html><html>...</html>",
          "designNotes": "说明你的设计选择",
          "warnings": []
        }
        - kind 必须等于字符串 "html_card"
        - title 不能为空
        - html 必须是完整 HTML 文档(包含 <html> 和 </html>)
        - html 中禁止 <script>、外链(http/https)、外链资源(src/href)、@import、url(...)

        ## 行为要求
        - 必须输出严格 JSON,不要输出 Markdown 包裹
        - 详细的视觉/布局/质量规则可通过 read_skill 工具读取完整 SKILL.md
        """;
  }

  private String buildUserPrompt(GenerateContentRequest request) {
    if ("article".equals(request.generationMode())) {
      return "请根据下面文章生成 CardFlow html_card JSON：\n" + safe(request.articleInput() == null ? "" : request.articleInput().body());
    }
    return buildTopicPrompt(request);
  }

  private String buildTopicPrompt(GenerateContentRequest request) {
    TopicInput topic = request.topicInput();
    OutputSpec output = outputSpec(request.outputFormat());
    return """
        请根据下面主题生成一张动态 HTML 信息卡片 json：
        平台规格：%s
        画布宽高：%dpx × %dpx
        内容策略：%s
        主题标题：%s
        副标题或上下文：%s
        补充说明：%s
        """.formatted(
        output.label(), output.width(), output.height(), output.strategy(),
        safe(topic == null ? "" : topic.title()),
        safe(topic == null ? "" : topic.subtitle()),
        safe(topic == null ? "" : topic.instructions())
    );
  }

  private OutputSpec outputSpec(String outputFormat) {
    return switch (safe(outputFormat)) {
      case "youtube_16_9" -> new OutputSpec("YouTube 封面 16:9", 1280, 720, "优先强标题、强对比、少文字，适合作为视频封面。");
      case "bilibili_16_9" -> new OutputSpec("B站封面 16:9", 1280, 720, "优先强标题、视觉冲突和二次元/知识区封面可读性。");
      case "douyin_9_16" -> new OutputSpec("抖音竖屏封面 9:16", 900, 1600, "优先竖屏视觉冲击，标题靠中上，避免底部交互区。");
      default -> new OutputSpec("小红书知识卡片 3:4", 900, 1200, "优先知识卡片结构，可使用清单、对比、流程、概念图等信息版式。");
    };
  }

  private record OutputSpec(String label, int width, int height, String strategy) {}

  private String inputText(GenerateContentRequest request) {
    if ("article".equals(request.generationMode()) && request.articleInput() != null) {
      return safe(request.articleInput().body());
    }
    TopicInput topic = request.topicInput();
    return topic == null ? "" : safe(topic.title()) + "\n" + safe(topic.subtitle()) + "\n" + safe(topic.instructions());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON", e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}