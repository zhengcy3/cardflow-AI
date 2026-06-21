package ai.cardflow.api.poster;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 校验 AI 知识海报模式返回的结构化 JSON。
 */
@Component
public class KnowledgePosterValidator {

  private static final int MIN_POINTS = 3;
  private static final int MAX_POINTS = 3;
  private static final java.util.regex.Pattern CHINESE = java.util.regex.Pattern.compile("[\\u4e00-\\u9fff]");

  /**
   * 校验模型返回的 JSON 是否符合 ai_knowledge_poster 协议。
   *
   * @param root 模型返回 JSON
   * @throws IllegalStateException 协议被违反时
   */
  public void validate(JsonNode root) {
    if (!"ai_knowledge_poster".equals(root.path("kind").asText())) {
      throw new IllegalStateException("AI 返回的 kind 必须是 ai_knowledge_poster。");
    }
    if (root.path("title").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 title 不能为空。");
    }
    assertContainsChinese(root.path("title").asText(), "title");
    JsonNode points = root.path("points");
    if (!points.isArray() || points.size() < MIN_POINTS) {
      throw new IllegalStateException("AI 返回的 points 至少需要 " + MIN_POINTS + " 条。");
    }
    if (points.size() > MAX_POINTS) {
      throw new IllegalStateException("AI 返回的 points 最多 " + MAX_POINTS + " 条。");
    }
    for (JsonNode point : points) {
      if (point.asText("").isBlank()) {
        throw new IllegalStateException("AI 返回的 points 不能包含空项。");
      }
      assertContainsChinese(point.asText(), "points");
    }
    if (root.path("sceneDescription").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 sceneDescription 不能为空。");
    }
    if (root.path("layout").asText("").isBlank()) {
      throw new IllegalStateException("AI 返回的 layout 不能为空。");
    }
  }

  private void assertContainsChinese(String value, String field) {
    if (!CHINESE.matcher(value).find()) {
      throw new IllegalStateException("AI 返回的 " + field + " 必须包含简体中文。");
    }
  }
}
