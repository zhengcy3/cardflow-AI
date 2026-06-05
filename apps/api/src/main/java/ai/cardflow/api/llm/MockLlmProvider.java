package ai.cardflow.api.llm;

import ai.cardflow.api.model.ApiModels.ArticleInput;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Mock 版 LLM Provider。
 *
 * <p>MVP 先用确定性 JSON 跑通端到端链路，后续真实模型只需要替换 Provider 实现。</p>
 */
@Component
public class MockLlmProvider implements LlmProvider {
  private final ObjectMapper objectMapper;

  /**
   * 注入 JSON 序列化工具。
   *
   * @param objectMapper Jackson 对象映射器
   */
  public MockLlmProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * 返回当前 Provider 名称。
   *
   * @return mock-provider
   */
  @Override
  public String name() {
    return "mock-provider";
  }

  /**
   * 根据生成模式返回单卡或轮播卡 JSON。
   *
   * @param request 内容生成请求
   * @return 结构化内容 JSON
   */
  @Override
  public String generateContent(GenerateContentRequest request) {
    if ("article".equals(request.generationMode())) {
      return articleContent(request.articleInput());
    }
    return topicContent(request.topicInput());
  }

  /**
   * 生成文章拆解模式的轮播卡片 JSON。
   *
   * @param article 文章输入
   * @return 轮播卡结构 JSON
   */
  private String articleContent(ArticleInput article) {
    String title = "长文拆解：把观点变成卡片";
    if (article != null && safe(article.extractionGoal()).equals("cover_copy")) {
      // cover_copy 模式用于模拟“只生成封面文案”的拆解目标。
      title = "这篇文章，值得做成一张封面";
    }
    return toJson(Map.of(
      "type", "carousel_card",
      "title", title,
      "pages", List.of(
        Map.of("role", "cover", "title", title, "subtitle", "AI 已提炼核心表达"),
        Map.of("role", "point", "title", "先提炼主张", "body", "从长文中找到最值得传播的一句话。"),
        Map.of("role", "point", "title", "再拆成结构", "body", "把观点拆成封面、论点和总结页。"),
        Map.of("role", "summary", "title", "最后生成卡片", "body", "用稳定模板输出可发布的视觉内容。")
      )
    ));
  }

  /**
   * 生成主题模式的单卡 JSON。
   *
   * @param topic 主题输入
   * @return 单卡结构 JSON
   */
  private String topicContent(TopicInput topic) {
    String title = topic == null || safe(topic.title()).isBlank() ? "为什么学了很多却还是没成长？" : topic.title();
    String subtitle = topic == null || safe(topic.subtitle()).isBlank() ? "消费知识不等于成长" : topic.subtitle();
    return toJson(Map.of(
      "type", "single_card",
      "title", title,
      "subtitle", subtitle,
      "summary", "成长不是输入更多信息，而是完成从理解到行动的闭环。",
      "points", List.of("收藏不等于行动", "知道不等于做到", "输入不等于输出"),
      "author", "CardFlow AI"
    ));
  }

  /**
   * 将 Mock 内容序列化为 JSON 字符串。
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
