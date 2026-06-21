package ai.cardflow.api.poster;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class KnowledgePosterValidatorTest {
  private final KnowledgePosterValidator validator = new KnowledgePosterValidator();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsValidPosterJson() throws Exception {
    validator.validate(objectMapper.readTree("""
        {
          "kind": "ai_knowledge_poster",
          "title": "学了很多 却还是 没成长？",
          "points": ["学 ≠ 会用", "输入 ≠ 成长", "方法比努力更重要"],
          "sceneDescription": "study desk scene",
          "layout": "left text right photo"
        }
        """));
  }

  @Test
  void rejectsTooFewPoints() {
    assertThatThrownBy(() -> validator.validate(objectMapper.readTree("""
        {
          "kind": "ai_knowledge_poster",
          "title": "标题",
          "points": ["一条"],
          "sceneDescription": "scene",
          "layout": "layout"
        }
        """))).hasMessageContaining("points");
  }

  @Test
  void rejectsEnglishOnlyTitle() {
    assertThatThrownBy(() -> validator.validate(objectMapper.readTree("""
        {
          "kind": "ai_knowledge_poster",
          "title": "COMMON PITFALLS",
          "points": ["learn more", "read books", "keep trying"],
          "sceneDescription": "scene",
          "layout": "layout"
        }
        """))).hasMessageContaining("简体中文");
  }
}
