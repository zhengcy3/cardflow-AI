package ai.cardflow.api.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CreativeImageValidatorTest {
  private final CreativeImageValidator validator = new CreativeImageValidator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsValidCreativeImageJson() throws Exception {
    validator.validate(objectMapper.readTree("""
        {
          "kind": "ai_creative_image",
          "title": "成长困局封面",
          "subtitle": "消费知识不等于成长",
          "coreTension": "输入很多但没有行动",
          "visualMetaphor": "左边堆书右边空转齿轮",
          "prompt": "A minimalist cover illustration about personal growth",
          "styleNotes": "warm tones"
        }
        """));
  }

  @Test
  void rejectsMissingPrompt() throws Exception {
    assertThatThrownBy(() -> validator.validate(objectMapper.readTree("""
        {
          "kind": "ai_creative_image",
          "title": "成长困局封面",
          "coreTension": "输入很多但没有行动",
          "visualMetaphor": "左边堆书右边空转齿轮",
          "prompt": ""
        }
        """))).hasMessageContaining("prompt");
  }
}
