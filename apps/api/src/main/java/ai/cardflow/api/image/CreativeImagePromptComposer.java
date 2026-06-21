package ai.cardflow.api.image;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 把 AI 创意图 JSON 合成为 MiniMax 最终生图 prompt。
 *
 * <p>仅传 LLM 生成的 scene prompt 容易产出与标题无关的泛化氛围图；
 * 合成时强制带上标题、核心观点和视觉隐喻约束。</p>
 */
public final class CreativeImagePromptComposer {
  private static final int MAX_MINIMAX_PROMPT = 1500;

  private CreativeImagePromptComposer() {}

  /**
   * 从作品内容 JSON 合成 MiniMax prompt。
   *
   * @param content ai_creative_image 结构化 JSON
   * @return 不超过 MiniMax 上限的最终 prompt
   */
  public static String toMiniMaxPrompt(JsonNode content) {
    String title = text(content, "title");
    String subtitle = text(content, "subtitle");
    String coreTension = text(content, "coreTension");
    String visualMetaphor = text(content, "visualMetaphor");
    String scene = text(content, "prompt");
    String style = text(content, "styleNotes");

    StringBuilder prompt = new StringBuilder();
    prompt.append("Social media cover image. The picture MUST clearly express this specific theme, not a generic atmosphere.\n");
    prompt.append("Title: ").append(title).append('\n');
    if (!subtitle.isBlank()) {
      prompt.append("Subtitle/context: ").append(subtitle).append('\n');
    }
    if (!coreTension.isBlank()) {
      prompt.append("Core message to show: ").append(coreTension).append('\n');
    }
    if (!visualMetaphor.isBlank()) {
      prompt.append("Required visual metaphor (must be obvious in the image): ").append(visualMetaphor).append('\n');
    }
    prompt.append("Scene: ").append(scene).append('\n');
    if (!style.isBlank()) {
      prompt.append("Style: ").append(style).append('\n');
    }
    prompt.append("""
        Constraints:
        - Every major visual element must support the title message.
        - Avoid unrelated generic stock motifs (decorative book stacks, random plants, abstract light beams, floating icons) unless they directly illustrate the core message.
        - Prefer one clear metaphor or contrast over pretty but meaningless ambiance.
        """);

    return truncate(prompt.toString().trim(), MAX_MINIMAX_PROMPT);
  }

  private static String text(JsonNode content, String field) {
    return content.path(field).asText("").trim();
  }

  private static String truncate(String value, int maxLength) {
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
