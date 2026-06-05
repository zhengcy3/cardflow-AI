package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.llm.LlmProvider;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 内容生成服务。
 *
 * <p>负责记录生成任务、调用 {@link LlmProvider}、写入用量记录，并把结构化 JSON 返回给前端。</p>
 */
@Service
public class GenerationService {
  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final ObjectMapper objectMapper;
  private final LlmProvider llmProvider;

  /**
   * 注入生成链路依赖。
   *
   * @param jdbc SQLite 访问入口
   * @param properties 应用配置
   * @param objectMapper JSON 序列化工具
   * @param llmProvider 当前启用的 LLM Provider
   */
  public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ObjectMapper objectMapper,
    LlmProvider llmProvider
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.llmProvider = llmProvider;
  }

  /**
   * 执行一次内容生成。
   *
   * <p>方法会先创建 running 状态任务；Provider 成功后写入用量记录，失败时把任务标记为 failed。</p>
   *
   * @param request 内容生成请求
   * @return 生成任务 ID 和结构化内容 JSON
   */
  public GenerateContentResponse generate(GenerateContentRequest request) {
    String taskId = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    String inputText = inputText(request);
    // 记录影响生成结果的关键参数，便于后续排查同一输入在不同模板或比例下的表现。
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
      String contentJson = llmProvider.generateContent(request);
      String finishedAt = Instant.now().toString();
      // 内容生成当前是同步完成，但仍保留任务表，方便后续替换为异步真实 LLM。
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
        llmProvider.name(),
        finishedAt
      );

      return new GenerateContentResponse(taskId, contentJson);
    } catch (RuntimeException e) {
      String finishedAt = Instant.now().toString();
      // 保留 Provider 抛出的错误信息，前端可以通过任务状态接口展示失败原因。
      jdbc.update(
        "update generate_task set status = ?, error_message = ?, finished_at = ? where id = ?",
        "failed",
        e.getMessage(),
        finishedAt,
        taskId
      );
      throw e;
    }
  }

  /**
   * 提取用于任务追踪的原始输入文本。
   *
   * @param request 内容生成请求
   * @return 主题字段拼接结果或文章正文
   */
  private String inputText(GenerateContentRequest request) {
    if ("article".equals(request.generationMode()) && request.articleInput() != null) {
      return safe(request.articleInput().body());
    }
    TopicInput topic = request.topicInput();
    return topic == null ? "" : safe(topic.title()) + "\n" + safe(topic.subtitle()) + "\n" + safe(topic.instructions());
  }

  /**
   * 将任务参数序列化为 JSON。
   *
   * @param value 任意可序列化对象
   * @return JSON 字符串
   */
  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON", e);
    }
  }

  /**
   * 将 null 字符串归一为空字符串。
   *
   * @param value 原始字符串
   * @return 非 null 字符串
   */
  private String safe(String value) {
    return value == null ? "" : value;
  }
}
