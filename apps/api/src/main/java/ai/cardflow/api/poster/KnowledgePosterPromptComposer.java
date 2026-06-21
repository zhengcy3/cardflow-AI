package ai.cardflow.api.poster;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 把 AI 知识海报 JSON 合成为 MiniMax 一次性出整图的 prompt。
 */
public final class KnowledgePosterPromptComposer {
  private static final int MAX_MINIMAX_PROMPT = 1500;

  private KnowledgePosterPromptComposer() {}

  public static String toMiniMaxPrompt(JsonNode content) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("""
        Design a clean Xiaohongshu knowledge poster (3:4). Flat editorial infographic style.
        High-quality, sharp edges, professional layout. NOT noisy, NOT cluttered.
        """);

    prompt.append("""
        
        CRITICAL LANGUAGE + LEGIBILITY:
        - ALL visible text MUST be accurate Simplified Chinese (简体中文), large and readable.
        - Render ONLY the exact Chinese strings listed below. No fake/gibberish characters.
        - Do NOT use English words. Do NOT invent unreadable pseudo-Chinese glyphs.
        - Use 3 bullet points maximum. Large headline typography. Plenty of whitespace.
        """);

    appendLine(prompt, "Aspect ratio", text(content, "aspectRatio"));
    appendLine(prompt, "Layout", text(content, "layout"));
    appendLine(prompt, "Color palette", text(content, "colorPalette"));

    prompt.append("\n简体中文（原样渲染，大字清晰）:\n");
    appendLine(prompt, "Headline", text(content, "title"));
    appendLine(prompt, "Subtitle", text(content, "subtitle"));
    appendLine(prompt, "Accent", text(content, "handwrittenAccent"));

    JsonNode points = content.path("points");
    if (points.isArray() && !points.isEmpty()) {
      int limit = Math.min(points.size(), 3);
      prompt.append("3 bullet points with simple check icons:\n");
      for (int i = 0; i < limit; i++) {
        prompt.append(i + 1).append(". ").append(points.get(i).asText("").trim()).append('\n');
      }
    }

    appendLine(prompt, "Callout", text(content, "callout"));

    JsonNode stickyNotes = content.path("stickyNotes");
    if (stickyNotes.isArray() && !stickyNotes.isEmpty()) {
      int limit = Math.min(stickyNotes.size(), 2);
      prompt.append("Optional sticky notes: ");
      for (int i = 0; i < limit; i++) {
        if (i > 0) {
          prompt.append(" / ");
        }
        prompt.append(stickyNotes.get(i).asText("").trim());
      }
      prompt.append('\n');
    }

    appendLine(prompt, "Illustration scene", text(content, "sceneDescription"));
    appendLine(prompt, "Style", text(content, "styleNotes"));

    prompt.append("""
        
        Quality requirements:
        - Simple composition: headline area + 3 bullets + one small illustration corner.
        - Avoid crossed swords, random icons, floating gibberish labels, or duplicate numbering.
        - Warm beige/cream palette, modern Chinese social media poster.
        """);

    return truncate(prompt.toString().trim(), MAX_MINIMAX_PROMPT);
  }

  private static void appendLine(StringBuilder prompt, String label, String value) {
    if (value.isBlank()) {
      return;
    }
    prompt.append(label).append(": ").append(value).append('\n');
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
