package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.exception.ApiErrorMessages;
import ai.cardflow.api.llm.LlmCallContext;
import ai.cardflow.api.llm.LlmCallStats;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.TopicInput;
import ai.cardflow.api.skill.SkillRegistry;
import ai.cardflow.api.poster.KnowledgePosterValidator;
import ai.cardflow.api.image.AspectRatioMapper;
import ai.cardflow.api.image.ImageStylePresets;
import ai.cardflow.api.image.ImageStylePresets.ImageStyle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.io.JsonEOFException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
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
  private static final String CREATIVE_IMAGE_SKILL = "cardflow.creative-image-generator";
  private static final String KNOWLEDGE_POSTER_SKILL = "cardflow.knowledge-poster-generator";
  private static final int RETRY_MAX_TOKENS = 8192;

  private final String modelName;
  private final int maxTokens;

  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final ChatClient chatClient;
  private final SkillRegistry skillRegistry;
  private final HtmlCardValidator validator;
  private final CreativeImageValidator creativeImageValidator;
  private final KnowledgePosterValidator knowledgePosterValidator;
  private final ObjectMapper objectMapper;

  public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ChatClient chatClient,
    SkillRegistry skillRegistry,
    HtmlCardValidator validator,
    CreativeImageValidator creativeImageValidator,
    KnowledgePosterValidator knowledgePosterValidator,
    ObjectMapper objectMapper,
    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") String modelName,
    @Value("${spring.ai.openai.chat.options.max-tokens:4096}") int maxTokens
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.chatClient = chatClient;
    this.skillRegistry = skillRegistry;
    this.validator = validator;
    this.creativeImageValidator = creativeImageValidator;
    this.knowledgePosterValidator = knowledgePosterValidator;
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
      if (isKnowledgePosterMode(request)) {
        contentJson = generateValidated(
          buildKnowledgePosterSystemPrompt(),
          buildKnowledgePosterUserPrompt(request),
          knowledgePosterValidator::validate,
          "文案精简"
        );
      } else if (isCreativeImageMode(request)) {
        contentJson = generateValidated(
          buildCreativeImageSystemPrompt(),
          buildCreativeImageUserPrompt(request),
          creativeImageValidator::validate,
          "描述精简"
        );
      } else {
        contentJson = generateValidated(
          buildSystemPrompt(),
          buildUserPrompt(request),
          validator::validate,
          "html/CSS 精简、模块≤6"
        );
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

  private String generateValidated(
    String systemPrompt,
    String userPrompt,
    Consumer<JsonNode> validator,
    String compactHint
  ) {
    String contentJson = callLlm(systemPrompt, userPrompt, maxTokens);
    try {
      validator.accept(parseContentJson(contentJson));
      return contentJson;
    } catch (Exception e) {
      if (!isJsonTruncationError(e) || !shouldRetryTruncatedOutput(LlmCallContext.snapshot(), maxTokens)) {
        throw e;
      }
      log.warn(
        "LLM output likely truncated at maxTokens={} completionTokens={}, retrying with {}",
        maxTokens,
        LlmCallContext.snapshot().completionTokens(),
        RETRY_MAX_TOKENS
      );
      String retryPrompt = userPrompt + """

          重要：上次输出因长度限制被截断。请重新生成完整 JSON，保持 %s，确保一次输出完整闭合。
          """.formatted(compactHint);
      contentJson = callLlm(systemPrompt, retryPrompt, RETRY_MAX_TOKENS);
      validator.accept(parseContentJson(contentJson));
      return contentJson;
    }
  }

  private JsonNode parseContentJson(String contentJson) {
    try {
      return objectMapper.readTree(contentJson);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private String callLlm(String systemPrompt, String userPrompt, int tokenLimit) {
    return chatClient.prompt()
      .options(OpenAiChatOptions.builder().maxTokens(tokenLimit).build())
      .system(systemPrompt)
      .user(userPrompt)
      .call()
      .content();
  }

  private static boolean shouldRetryTruncatedOutput(LlmCallStats stats, int tokenLimit) {
    if (stats == null || stats.callCount() <= 0) {
      return true;
    }
    return stats.completionTokens() >= (int) (tokenLimit * 0.95);
  }

  private static boolean isJsonTruncationError(Throwable error) {
    Throwable current = error;
    while (current != null) {
      if (current instanceof JsonEOFException) {
        return true;
      }
      String message = current.getMessage();
      if (message != null && (
        message.contains("Unexpected end-of-input")
          || message.contains("was expecting closing quote")
      )) {
        return true;
      }
      current = current.getCause();
    }
    return false;
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

  private String buildCreativeImageSystemPrompt() {
    return skillRegistry.readFullContent(CREATIVE_IMAGE_SKILL) + """

        ## 字符串引号规则
        - 字段值里的引号用中文「」,不要用英文双引号 "
        """;
  }

  private String buildKnowledgePosterSystemPrompt() {
    return skillRegistry.readFullContent(KNOWLEDGE_POSTER_SKILL) + """

        ## 字符串引号规则
        - 字段值里的引号用中文「」,不要用英文双引号 "
        """;
  }

  private ImageStyle resolveStyle(GenerateContentRequest request) {
    return ImageStylePresets.resolve(lookupStyleKey(request.templateId()));
  }

  private String lookupStyleKey(String templateId) {
    if (templateId == null || templateId.isBlank()) {
      return "xiaohongshu_highlight";
    }
    try {
      return jdbc.queryForObject(
        "select style_key from template where id = ?",
        String.class,
        templateId
      );
    } catch (Exception ignored) {
      return "xiaohongshu_highlight";
    }
  }

  private String styleBlock(ImageStyle style, String modeGuidance) {
    return """
        封面风格：%s（styleKey=%s）
        Palette：%s · Rendering：%s
        风格约束：%s
        """.formatted(style.displayName(), style.key(), style.palette(), style.rendering(), modeGuidance);
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

  private boolean isKnowledgePosterMode(GenerateContentRequest request) {
    return "ai_knowledge_poster".equals(request.renderMode());
  }

  private String buildKnowledgePosterUserPrompt(GenerateContentRequest request) {
    OutputSpec output = posterOutputSpec(request.outputFormat());
    ImageStyle style = resolveStyle(request).forKnowledgePoster();
    String styleSection = styleBlock(style, style.knowledgePosterGuidance());
    if ("article".equals(request.generationMode())) {
      return """
          请根据下面文章生成一张 AI 知识海报 JSON（一次性出整图，含标题、3要点、场景描述）：
          %s
          平台规格：%s
          画面比例：%s
          海报策略：%s
          文章正文：
          %s
          """.formatted(
          styleSection,
          output.label(), output.aspectRatio(), output.strategy(),
          safe(request.articleInput() == null ? "" : request.articleInput().body())
      );
    }
    TopicInput topic = request.topicInput();
    return """
        请根据下面主题生成一张 AI 知识海报 JSON（一次性出整图，含标题、3要点、场景描述）：
        %s
        平台规格：%s
        画面比例：%s
        海报策略：%s
        主题标题：%s
        副标题或上下文：%s
        补充说明：%s
        """.formatted(
        styleSection,
        output.label(), output.aspectRatio(), output.strategy(),
        safe(topic == null ? "" : topic.title()),
        safe(topic == null ? "" : topic.subtitle()),
        safe(topic == null ? "" : topic.instructions())
    );
  }

  private OutputSpec posterOutputSpec(String outputFormat) {
    String format = safe(outputFormat);
    String aspectRatio = AspectRatioMapper.fromOutputFormat(format);
    return switch (format) {
      case "youtube_16_9" -> new OutputSpec("YouTube 知识海报 16:9", 1280, 720, aspectRatio,
        "横版海报：左侧大标题+3要点，右侧人物/场景摄影，强对比，适合视频封面。");
      case "bilibili_16_9" -> new OutputSpec("B站知识海报 16:9", 1280, 720, aspectRatio,
        "横版知识区海报：大标题+要点清单+场景图，信息层级清晰。");
      case "douyin_9_16" -> new OutputSpec("抖音知识海报 9:16", 900, 1600, aspectRatio,
        "竖屏海报：标题靠上，要点居中，下方场景图，适合竖屏封面。");
      default -> new OutputSpec("小红书知识海报 3:4", 900, 1200, aspectRatio,
        "竖版海报：大标题+3条短要点+小场景插画；文字要少、字号要大；全部简体中文；避免信息堆砌。");
    };
  }

  private String buildCreativeImageUserPrompt(GenerateContentRequest request) {
    OutputSpec output = creativeOutputSpec(request.outputFormat());
    ImageStyle style = resolveStyle(request);
    String styleSection = styleBlock(style, style.creativeImageGuidance());
    if ("article".equals(request.generationMode())) {
      return """
          请根据下面文章生成一张与核心观点强关联的 AI 创意封面 prompt JSON：
          %s
          平台规格：%s
          画面比例：%s
          封面策略：%s
          文章正文：
          %s
          """.formatted(
          styleSection,
          output.label(), output.aspectRatio(), output.strategy(),
          safe(request.articleInput() == null ? "" : request.articleInput().body())
      );
    }
    TopicInput topic = request.topicInput();
    return """
        请根据下面主题生成一张与标题强关联的 AI 创意封面 prompt JSON：
        %s
        平台规格：%s
        画面比例：%s
        封面策略：%s
        主题标题（优先作为 JSON 的 title）：%s
        副标题或上下文（写入 JSON 的 subtitle）：%s
        补充说明：%s
        """.formatted(
        styleSection,
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
    ImageStyle style = resolveStyle(request);
    String styleSection = styleBlock(style, style.htmlCardGuidance());
    return """
        请根据下面主题生成一张动态 HTML 信息卡片 json：
        %s
        平台规格：%s
        画布宽高：%dpx × %dpx
        内容策略：%s
        主题标题：%s
        副标题或上下文：%s
        补充说明：%s
        """.formatted(
        styleSection,
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