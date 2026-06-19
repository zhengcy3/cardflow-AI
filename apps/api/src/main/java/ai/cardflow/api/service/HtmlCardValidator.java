package ai.cardflow.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 校验 DeepSeek 返回的 html_card JSON 是否符合 CardFlow 协议。
 *
 * <p>从 {@code DeepSeekLlmProvider} 提取而来,逻辑不变。</p>
 */
@Component
public class HtmlCardValidator {

  /**
   * 校验模型返回的 JSON 是否符合 html_card 协议和基础安全边界。
   *
   * @param root 模型返回 JSON
   * @throws IllegalStateException 协议被违反时
   */
  public void validate(JsonNode root) {
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
    String lower = html.toLowerCase();
    if (!lower.contains("<html") || !lower.contains("</html>")) {
      throw new IllegalStateException("DeepSeek 返回的 html 必须是完整 HTML 文档。");
    }
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
}