package ai.cardflow.api.poster;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KnowledgePosterPromptComposerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void buildsIntegratedPosterPromptWithTextAndScene() throws Exception {
    var content = objectMapper.readTree("""
        {
          "kind": "ai_knowledge_poster",
          "title": "学了很多 却还是 没成长？",
          "subtitle": "消费知识不等于成长",
          "eyebrow": "为什么",
          "handwrittenAccent": "越努力，越迷茫？",
          "points": ["学 ≠ 会用", "输入 ≠ 成长", "方法比努力更重要"],
          "callout": "知识要转化 才能真正改变你",
          "stickyNotes": ["听过", "看过", "记住了", "但还是没改变"],
          "sceneDescription": "tired young woman resting head on desk beside tall book stack, warm desk lamp",
          "layout": "left text hierarchy, bottom-right realistic photo scene",
          "colorPalette": "warm beige cream brown",
          "platformStyle": "Xiaohongshu knowledge poster",
          "aspectRatio": "3:4",
          "styleNotes": "mixed typography with checkmarks"
        }
        """);

    String prompt = KnowledgePosterPromptComposer.toMiniMaxPrompt(content);

    assertThat(prompt).contains("knowledge poster");
    assertThat(prompt).contains("学了很多 却还是 没成长？");
    assertThat(prompt).contains("学 ≠ 会用");
    assertThat(prompt).contains("tired young woman");
    assertThat(prompt).contains("简体中文");
    assertThat(prompt).contains("gibberish");
    assertThat(prompt).contains("NEVER overlap");
    assertThat(prompt).contains("Card 1");
  }
}
