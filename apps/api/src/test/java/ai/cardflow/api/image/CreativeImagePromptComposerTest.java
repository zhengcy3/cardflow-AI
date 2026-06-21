package ai.cardflow.api.image;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CreativeImagePromptComposerTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void includesTitleAndVisualMetaphorInMiniMaxPrompt() throws Exception {
    var content = objectMapper.readTree("""
        {
          "kind": "ai_creative_image",
          "title": "消费知识不等于成长",
          "subtitle": "为什么学了很多却还是没成长",
          "coreTension": "输入很多知识但没有行动，所以不算成长",
          "visualMetaphor": "左侧堆满未打开的书，右侧一个空转齿轮，形成对比",
          "prompt": "Split composition, muted teal palette, cinematic lighting",
          "styleNotes": "小红书知识封面"
        }
        """);

    String prompt = CreativeImagePromptComposer.toMiniMaxPrompt(content);

    assertThat(prompt).contains("消费知识不等于成长");
    assertThat(prompt).contains("为什么学了很多却还是没成长");
    assertThat(prompt).contains("空转齿轮");
    assertThat(prompt).contains("Split composition");
    assertThat(prompt).contains("Avoid unrelated generic stock motifs");
  }
}
