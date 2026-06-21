package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.exception.ApiErrorMessages;
import ai.cardflow.api.llm.LlmCallContext;
import ai.cardflow.api.llm.LlmCallStats;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.TopicInput;
import ai.cardflow.api.skill.SkillRegistry;
import ai.cardflow.api.image.AspectRatioMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private static final Logger log = LoggerFactory.getLogger(GenerationService.class);
  private static final String HTML_CARD_SKILL = "cardflow.html-card-generator";
  private static final String CREATIVE_IMAGE_SYSTEM_PROMPT = """
      你是 CardFlow AI 创意图提示词工程师。任务：把用户主题转成「与标题强关联」的 MiniMax 文生图 prompt。

      只返回 JSON，不要 Markdown 代码块：
      {
        "kind": "ai_creative_image",
        "title": "中文封面标题，优先使用用户主题标题",
        "subtitle": "中文副标题或补充语境，可为空字符串",
        "coreTension": "一句话说明标题的核心观点/矛盾/对比（中文）",
        "visualMetaphor": "必须用画面表达的具体视觉隐喻（中文），例如：左边堆满未拆封的书，右边一颗很小的芽，强调输入≠成长",
        "prompt": "英文或中英混合的详细画面描述，必须落实 visualMetaphor",
        "styleNotes": "简短风格说明，如色调、摄影/插画风格、平台感"
      }

      规则：
      - kind 固定为 ai_creative_image
      - title/subtitle 必须来自用户输入，不要自造无关标题
      - coreTension 必须紧扣标题观点，写出「真正要表达什么」
      - visualMetaphor 必须具体、可画，禁止「书籍+植物+光效」等泛化意象，除非与用户观点直接相关
      - prompt 不超过 900 字符，描述主体、构图、对比关系、光线、色调；不要要求画面出现大段可读文字
      - 竖屏封面主体居中，横屏封面强对比+留白
      """;

  private final String modelName;
  private final int maxTokens;

  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final ChatClient chatClient;
  private final SkillRegistry skillRegistry;
  private final HtmlCardValidator validator;
  private final CreativeImageValidator creativeImageValidator;
  private final ObjectMapper objectMapper;

  public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ChatClient chatClient,
    SkillRegistry skillRegistry,
    HtmlCardValidator validator,
    CreativeImageValidator creativeImageValidator,
    ObjectMapper objectMapper,
    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") String modelName,
    @Value("${spring.ai.openai.chat.options.max-tokens:2048}") int maxTokens
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.chatClient = chatClient;
    this.skillRegistry = skillRegistry;
    this.validator = validator;
    this.creativeImageValidator = creativeImageValidator;
    this.objectMapper = objectMapper;
    this.modelName = modelName;
    this.maxTokens = maxTokens;
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

    LlmCallContext.begin(taskId);
    log.info(
      "Content generation started taskId={} model={} maxTokens={} mode={} outputFormat={}",
      taskId,
      modelName,
      maxTokens,
      request.generationMode(),
      request.outputFormat()
    );

    try {
      String contentJson;
      if (isCreativeImageMode(request)) {
        contentJson = chatClient.prompt()
          .system(CREATIVE_IMAGE_SYSTEM_PROMPT)
          .user(buildCreativeImageUserPrompt(request))
          .call()
          .content();
        creativeImageValidator.validate(objectMapper.readTree(contentJson));
      } else {
        contentJson = chatClient.prompt()
          .system(buildSystemPrompt())
          .user(buildUserPrompt(request))
          .call()
          .content();
        validator.validate(objectMapper.readTree(contentJson));
      }
      String finishedAt = Instant.now().toString();
      jdbc.update("update generate_task set status = ?, finished_at = ? where id = ?", "succeeded", finishedAt, taskId);
      recordUsage(taskId, finishedAt);
      log.info("Content generation succeeded taskId={} {}", taskId, formatStats(LlmCallContext.snapshot()));
      return new GenerateContentResponse(taskId, contentJson);
    } catch (Exception e) {
      String finishedAt = Instant.now().toString();
      String userMessage = ApiErrorMessages.toUserMessage(e);
      jdbc.update(
        "update generate_task set status = ?, error_message = ?, finished_at = ? where id = ?",
        "failed", userMessage, finishedAt, taskId
      );
      LlmCallStats stats = LlmCallContext.snapshot();
      if (stats != null && stats.callCount() > 0) {
        recordUsage(taskId, finishedAt);
      }
      log.warn("Content generation failed taskId={} {} error={}", taskId, formatStats(stats), e.toString(), e);
      throw new IllegalStateException(userMessage);
    } finally {
      LlmCallContext.clear();
    }
  }

  private void recordUsage(String taskId, String finishedAt) {
    int apiCalls = resolveApiCalls(LlmCallContext.snapshot());
    jdbc.update("""
        insert into usage_record (id, user_id, task_id, usage_type, amount, model_name, created_at)
        values (?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID().toString(),
        properties.app().defaultUserId(),
        taskId,
        "content_generation",
        apiCalls,
        modelName,
        finishedAt
    );
  }

  private static int resolveApiCalls(LlmCallStats stats) {
    if (stats == null || stats.callCount() <= 0) {
      return 1;
    }
    return stats.callCount();
  }

  private static String formatStats(LlmCallStats stats) {
    if (stats == null) {
      return "apiCalls=0 promptTokens=0 completionTokens=0";
    }
    return "apiCalls=%d promptTokens=%d completionTokens=%d totalTokens=%d".formatted(
      stats.callCount(),
      stats.promptTokens(),
      stats.completionTokens(),
      stats.totalTokens()
    );
  }

  private String buildSystemPrompt() {
    return skillRegistry.readFullContent(HTML_CARD_SKILL) + """

        ## 字符串引号规则（避免 JSON 解析失败）
        - 所有字段值里的引号必须用中文「」或『』,严禁使用英文双引号 "
        - 如需强调术语,请用中文引号：采用 balanced 布局,结合「清单陷阱」结构
        """;
  }

  private String buildUserPrompt(GenerateContentRequest request) {
    if ("article".equals(request.generationMode())) {
      return "请根据下面文章生成 CardFlow html_card JSON：\n" + safe(request.articleInput() == null ? "" : request.articleInput().body());
    }
    return buildTopicPrompt(request);
  }

  private boolean isCreativeImageMode(GenerateContentRequest request) {
    return "ai_creative_image".equals(request.renderMode());
  }

  private String buildCreativeImageUserPrompt(GenerateContentRequest request) {
    OutputSpec output = creativeOutputSpec(request.outputFormat());
    if ("article".equals(request.generationMode())) {
      return """
          请根据下面文章生成一张与核心观点强关联的 AI 创意封面 prompt JSON：
          平台规格：%s
          画面比例：%s
          封面策略：%s
          文章正文：
          %s
          """.formatted(
          output.label(), output.aspectRatio(), output.strategy(),
          safe(request.articleInput() == null ? "" : request.articleInput().body())
      );
    }
    TopicInput topic = request.topicInput();
    return """
        请根据下面主题生成一张与标题强关联的 AI 创意封面 prompt JSON：
        平台规格：%s
        画面比例：%s
        封面策略：%s
        主题标题（优先作为 JSON 的 title）：%s
        副标题或上下文（写入 JSON 的 subtitle）：%s
        补充说明：%s
        """.formatted(
        output.label(), output.aspectRatio(), output.strategy(),
        safe(topic == null ? "" : topic.title()),
        safe(topic == null ? "" : topic.subtitle()),
        safe(topic == null ? "" : topic.instructions())
    );
  }

  private OutputSpec creativeOutputSpec(String outputFormat) {
    String format = safe(outputFormat);
    String aspectRatio = AspectRatioMapper.fromOutputFormat(format);
    return switch (format) {
      case "youtube_16_9" -> new OutputSpec("YouTube 封面 16:9", 1280, 720, aspectRatio,
        "用单一视觉隐喻表达视频主题，强对比、少元素，适合 16:9 横版封面。");
      case "bilibili_16_9" -> new OutputSpec("B站封面 16:9", 1280, 720, aspectRatio,
        "突出一个与标题直接相关的视觉冲突或角色场景，适合知识/二次元封面。");
      case "douyin_9_16" -> new OutputSpec("抖音竖屏封面 9:16", 900, 1600, aspectRatio,
        "竖屏单一主体+明确隐喻，主体靠中上，一眼看懂主题。");
      default -> new OutputSpec("小红书封面 3:4", 900, 1200, aspectRatio,
        "封面必须让人不看文字也能感受到标题观点；用对比/失衡/空转等隐喻，避免泛化读书/发光/植物意象。");
    };
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
    String format = safe(outputFormat);
    String aspectRatio = AspectRatioMapper.fromOutputFormat(format);
    return switch (format) {
      case "youtube_16_9" -> new OutputSpec("YouTube 封面 16:9", 1280, 720, aspectRatio, "优先强标题、强对比、少文字，适合作为视频封面。");
      case "bilibili_16_9" -> new OutputSpec("B站封面 16:9", 1280, 720, aspectRatio, "优先强标题、视觉冲突和二次元/知识区封面可读性。");
      case "douyin_9_16" -> new OutputSpec("抖音竖屏封面 9:16", 900, 1600, aspectRatio, "优先竖屏视觉冲击，标题靠中上，避免底部交互区。");
      default -> new OutputSpec("小红书知识卡片 3:4", 900, 1200, aspectRatio, "优先知识卡片结构，可使用清单、对比、流程、概念图等信息版式。");
    };
  }

  private record OutputSpec(String label, int width, int height, String aspectRatio, String strategy) {}

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