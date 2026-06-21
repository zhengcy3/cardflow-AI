package ai.cardflow.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 校验 AI 创意图模式返回的结构化 JSON。
 */
@Component
public class CreativeImageValidator {

  private static final int MAX_PROMPT_LENGTH = 1500;

  /**
   * 校验模型返回的 JSON 是否符合 ai_creative_image 协议。
   *
   * @param root 模型返回 JSON
   * @throws IllegalStateException 协议被违反时
   */
  public void validate(JsonNode root) {
    if (!"ai_creative_image".equals(root.path("kind").asText())) {
      throw new IllegalStateException("AI 返回的 kind 必须是 ai_creative_image。");
    }
    if (root.path("title").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 title 不能为空。");
    }
    if (root.path("coreTension").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 coreTension 不能为空。");
    }
    if (root.path("visualMetaphor").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 visualMetaphor 不能为空。");
    }
    String prompt = root.path("prompt").asText("");
    if (prompt.isBlank()) {
      throw new IllegalStateException("AI 返回的 prompt 不能为空。");
    }
    if (prompt.length() > MAX_PROMPT_LENGTH) {
      throw new IllegalStateException("AI 返回的 prompt 长度不能超过 " + MAX_PROMPT_LENGTH + " 字符。");
    }
  }
}
