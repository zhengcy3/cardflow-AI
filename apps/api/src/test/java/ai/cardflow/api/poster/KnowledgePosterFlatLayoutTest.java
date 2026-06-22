package ai.cardflow.api.poster;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KnowledgePosterFlatLayoutTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void flatInfographicStyleUsesVerticalNonOverlappingLayout() throws Exception {
    var content = objectMapper.readTree("""
        {
          "kind": "ai_knowledge_poster",
          "title": "为什么学了很多 却还是 没成长？",
          "points": ["只收藏不行动", "无目标漫游", "只有输入没有输出"],
          "layout": "vertical single column",
          "styleKey": "flat_infographic"
        }
        """);

    String prompt = KnowledgePosterPromptComposer.toMiniMaxPrompt(content);

    assertThat(prompt).contains("FLAT INFOGRAPHIC LAYOUT");
    assertThat(prompt).contains("vertical column");
    assertThat(prompt).contains("must NEVER overlap");
  }
}
