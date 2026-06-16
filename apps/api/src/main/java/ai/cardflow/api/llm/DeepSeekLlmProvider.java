package ai.cardflow.api.llm;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * DeepSeek 真实 LLM Provider。
 *
 * <p>当前先接入主题生成，要求模型直接返回可被渲染器消费的动态 HTML JSON。</p>
 */
@Component
public class DeepSeekLlmProvider implements LlmProvider {
  private final AppProperties properties;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  /**
   * 注入 DeepSeek 调用依赖。
   *
   * @param properties 应用配置
   * @param objectMapper JSON 工具
   * @param restTemplate HTTP 客户端
   */
  public DeepSeekLlmProvider(AppProperties properties, ObjectMapper objectMapper, RestTemplate restTemplate) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
  }

  /**
   * 返回用量记录里的模型名称。
   *
   * @return DeepSeek 模型 ID
   */
  @Override
  public String name() {
    return llm().model();
  }

  /**
   * 调 DeepSeek 生成结构化内容。
   *
   * @param request 内容生成请求
   * @return 合法的结构化内容 JSON 字符串
   */
  @Override
  public String generateContent(GenerateContentRequest request) {
    if ("article".equals(request.generationMode())) {
      throw new IllegalStateException("文章生成尚未接入真实 AI 模型，请先使用主题生成。");
    }
    ensureConfigured();

    Map<String, Object> payload = Map.of(
      "model", llm().model(),
      "messages", List.of(
        Map.of("role", "system", "content", systemPrompt()),
        Map.of("role", "user", "content", topicPrompt(request))
      ),
      "response_format", Map.of("type", "json_object"),
      "temperature", 0.85,
      "max_tokens", llm().maxTokens()
    );
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(llm().apiKey());

    try {
      JsonNode response = restTemplate.postForObject(
        chatCompletionsUrl(),
        new HttpEntity<>(payload, headers),
        JsonNode.class
      );
      return normalizeContentJson(response);
    } catch (RestClientException e) {
      throw new IllegalStateException("DeepSeek 内容生成失败：" + e.getMessage(), e);
    }
  }

  /**
   * 确保必要配置已填写。
   */
  private void ensureConfigured() {
    if (!"deepseek".equalsIgnoreCase(safe(llm().provider()))) {
      throw new IllegalStateException("当前仅支持 DeepSeek Provider，请将 cardflow.llm.provider 设置为 deepseek。");
    }
    if (safe(llm().apiKey()).isBlank()) {
      throw new IllegalStateException("缺少 DeepSeek API Key，请设置环境变量 DEEPSEEK_API_KEY。");
    }
  }

  /**
   * 从 DeepSeek 响应中取出并校验模型返回的 JSON。
   *
   * @param response DeepSeek chat completion 响应
   * @return 归一化后的 JSON 字符串
   */
  private String normalizeContentJson(JsonNode response) {
    String content = response == null
      ? ""
      : response.path("choices").path(0).path("message").path("content").asText("");
    if (content.isBlank()) {
      throw new IllegalStateException("DeepSeek 返回内容为空，请稍后重试。");
    }

    try {
      JsonNode root = objectMapper.readTree(content);
      validateHtmlCard(root);
      return objectMapper.writeValueAsString(root);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("DeepSeek 返回的内容不是合法 JSON。", e);
    }
  }

  /**
   * 校验动态 HTML 卡片协议和基础安全边界。
   *
   * @param root 模型返回 JSON
   */
  private void validateHtmlCard(JsonNode root) {
    if (!"html_card".equals(root.path("kind").asText())) {
      throw new IllegalStateException("DeepSeek 返回的 kind 必须是 html_card。");
    }
    if (root.path("title").asText("").isBlank()) {
      throw new IllegalStateException("DeepSeek 返回的 title 不能为空。");
    }
    String html = root.path("html").asText("");
    if (html.isBlank()) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能为空。");
    }
    if (!html.toLowerCase().contains("<html") || !html.toLowerCase().contains("</html>")) {
      throw new IllegalStateException("DeepSeek 返回的 html 必须是完整 HTML 文档。");
    }
    assertSafeHtml(html);
  }

  /**
   * 禁止模型输出外链资源和脚本。
   *
   * @param html 模型返回 HTML
   */
  private void assertSafeHtml(String html) {
    String lower = html.toLowerCase();
    if (lower.contains("<script") || lower.contains("</script")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含 script。");
    }
    if (lower.contains("http://") || lower.contains("https://") || lower.contains("src=") || lower.contains("href=")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含外部资源。");
    }
    if (lower.contains("@import") || lower.contains("url(")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含外部样式资源。");
    }
  }

  /**
   * 主题生成系统提示词。
   *
   * @return 系统提示词
   */
  private String systemPrompt() {
    return String.join("\n\n",
      identitySection(),
      outputProtocolSection(),
      contentAnalysisSection(),
      layoutSelectionSection(),
      visualStyleSection(),
      htmlConstraintSection(),
      qualityGateSection()
    );
  }

  /**
   * 角色和总体目标。
   *
   * @return prompt 片段
   */
  private String identitySection() {
    return """
      ## 角色
      你是 CardFlow AI 的信息卡片 HTML 设计师。
      你必须输出严格 JSON，不要输出 Markdown，不要输出解释。
      你不需要填充固定的 title/summary/points 模板；你要根据主题实时设计信息结构和 HTML 版式。
      """;
  }

  /**
   * 固定外层交付协议。
   *
   * @return prompt 片段
   */
  private String outputProtocolSection() {
    return """
      ## 输出协议
      JSON 外层协议必须完全匹配：
      {
        "kind": "html_card",
        "title": "作品标题",
        "html": "<!doctype html>...",
        "designNotes": "说明你选择的内容结构、布局和视觉风格",
        "warnings": []
      }
      """;
  }

  /**
   * 借鉴 baoyu skills 的内容分析维度。
   *
   * @return prompt 片段
   */
  private String contentAnalysisSection() {
    return """
      ## 内容分析
      先在内部判断内容类型，但不要把分析过程输出给用户：
      - 观点反转：适合强标题、误区 vs 真相、关键洞见。
      - 清单/避坑：适合 list，强调编号、标签和重点词。
      - 对比：适合 comparison，左右或上下对照。
      - 流程/方法：适合 flow，使用箭头、步骤、闭环。
      - 概念关系：适合 mindmap / quadrant，突出结构和分类。
      - 强封面：适合 sparse，少字但视觉冲击强。
      """;
  }

  /**
   * 布局选择规则。
   *
   * @return prompt 片段
   */
  private String layoutSelectionSection() {
    return """
      ## 布局选择
      必须根据内容选择一种主布局，并在 designNotes 中写明：
      - sparse：1-2 个核心观点，适合封面和金句。
      - balanced：3-4 个模块，适合普通知识卡。
      - dense：5-8 个信息块，适合高密度干货，但要保证字号。
      - list：清单、排行、避坑指南。
      - comparison：误区 vs 正解、前后对比、A vs B。
      - flow：输入 → 输出 → 反馈 → 改变这类路径或闭环。
      - mindmap：中心概念 + 多个分支。
      - quadrant：四象限、分类、优先级。
      可选布局关键词：comparison / flow / list / mindmap / quadrant。
      """;
  }

  /**
   * 视觉风格规则。
   *
   * @return prompt 片段
   */
  private String visualStyleSection() {
    return """
      ## 视觉风格
      优先做小红书知识卡，而不是 PPT 或咨询报告：
      - 标题要强，关键词可以使用高亮、色块、描边、标签、下划线。
      - 至少使用 1 个视觉结构：箭头、路径、编号、闭环、对比轴、分组卡片之一。
      - 使用清晰层级：主标题 > 副标题/结论 > 模块标题 > 正文。
      - 正文字号必须保证手机可读，尽量短句化。
      - 避免只生成两个过高的大白色卡片；卡片高度应贴合内容。
      - 不要编造具体数据、名人、论文或出处。
      """;
  }

  /**
   * HTML 和安全约束。
   *
   * @return prompt 片段
   */
  private String htmlConstraintSection() {
    return """
      ## HTML 约束
      - 必须是完整 HTML 文档，包含 <!doctype html>、html、head、style、body。
      - 只允许内联 HTML + CSS，不允许 JavaScript。
      - 不允许 script、外链图片、外链字体、href、src、@import、url(...)。
      - 画布尺寸以用户提示中的平台规格为准，body margin 为 0，主容器宽高必须匹配该规格。
      - 所有内容必须在画布内，底部保留约 10%% 安全区，不要把关键信息放在最底部。
      - 中文字体使用系统字体栈：-apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif。
      """;
  }

  /**
   * 生成前自检规则。
   *
   * @return prompt 片段
   */
  private String qualityGateSection() {
    return """
      ## 质量约束
      输出前自检，并把无法满足的问题写入 warnings：
      - 不允许出现大面积空白：主要内容区至少覆盖画布 65%%。
      - 每个信息块必须有清晰标题和视觉区分。
      - 小红书 3:4 优先使用强标题 + 3-6 个模块，而不是长列表。
      - 如果使用对比布局，两侧卡片高度必须贴合内容，避免下半部分空白。
      - 每条正文尽量不超过 18 个中文字；长解释应拆成关键词或更短句。
      - 不要让文字贴边、溢出画布或落入底部安全区。
      """;
  }

  /**
   * 根据前端主题输入生成用户提示词。
   *
   * @param request 内容生成请求
   * @return 用户提示词
   */
  private String topicPrompt(GenerateContentRequest request) {
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
      output.label(),
      output.width(),
      output.height(),
      output.strategy(),
      safe(topic == null ? "" : topic.title()),
      safe(topic == null ? "" : topic.subtitle()),
      safe(topic == null ? "" : topic.instructions())
    );
  }

  /**
   * 根据输出格式给模型提供平台规格。
   *
   * @param outputFormat 前端选择的平台比例
   * @return 平台规格
   */
  private OutputSpec outputSpec(String outputFormat) {
    return switch (safe(outputFormat)) {
      case "youtube_16_9" -> new OutputSpec("YouTube 封面 16:9", 1280, 720, "优先强标题、强对比、少文字，适合作为视频封面。");
      case "bilibili_16_9" -> new OutputSpec("B站封面 16:9", 1280, 720, "优先强标题、视觉冲突和二次元/知识区封面可读性。");
      case "douyin_9_16" -> new OutputSpec("抖音竖屏封面 9:16", 900, 1600, "优先竖屏视觉冲击，标题靠中上，避免底部交互区。");
      default -> new OutputSpec("小红书知识卡片 3:4", 900, 1200, "优先知识卡片结构，可使用清单、对比、流程、概念图等信息版式。");
    };
  }

  /**
   * 模型输出规格。
   */
  private record OutputSpec(String label, int width, int height, String strategy) {}

  /**
   * DeepSeek chat completions URL。
   *
   * @return 完整 API URL
   */
  private String chatCompletionsUrl() {
    return safe(llm().baseUrl()).replaceAll("/+$", "") + "/chat/completions";
  }

  /**
   * 当前 LLM 配置。
   *
   * @return 配置值
   */
  private AppProperties.Llm llm() {
    return properties.llm();
  }

  /**
   * null 字符串归一为空字符串。
   *
   * @param value 原始值
   * @return 非 null 字符串
   */
  private String safe(String value) {
    return value == null ? "" : value;
  }
}
