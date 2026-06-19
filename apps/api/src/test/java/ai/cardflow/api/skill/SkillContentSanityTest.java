package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * 卡死 SKILL.md 中必须存在的关键内容,防止未来编辑时误删。
 */
class SkillContentSanityTest {

  @Test
  void htmlCardGeneratorSkillContainsAllSevenSections() throws Exception {
    String content = StreamUtils.copyToString(
      new ClassPathResource("skills/cardflow.html-card-generator/SKILL.md").getInputStream(),
      StandardCharsets.UTF_8
    );

    assertThat(content).contains("html_card");
    assertThat(content).contains("designNotes");
    assertThat(content).contains("布局选择");
    assertThat(content).contains("comparison");
    assertThat(content).contains("flow");
    assertThat(content).contains("mindmap");
    assertThat(content).contains("quadrant");
    assertThat(content).contains("不允许出现大面积空白");
    // Brief originally used "禁止 script"; SKILL.md actually says "不允许 script" (same meaning).
    // Match the on-disk wording to avoid false regressions.
    assertThat(content).contains("不允许 script").doesNotContain("placeholder");
  }

  @Test
  void skillSizeStaysUnderLimit() throws Exception {
    String content = StreamUtils.copyToString(
      new ClassPathResource("skills/cardflow.html-card-generator/SKILL.md").getInputStream(),
      StandardCharsets.UTF_8
    );

    assertThat(content.getBytes(StandardCharsets.UTF_8).length)
      .as("SKILL.md must stay under 4KB to avoid blowing context window")
      .isLessThan(4096);
  }
}