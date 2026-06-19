package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SkillSummaryFormatterTest {
  private final SkillSummaryFormatter formatter = new SkillSummaryFormatter();

  @Test
  void formatsEachSkillAsBulletLine() {
    SkillMeta a = new SkillMeta("cardflow.html-card-generator", "生成 CardFlow 信息卡", "topic 模式", List.of(), "1", new ByteArrayResource(new byte[0]));
    SkillMeta b = new SkillMeta("cardflow.article-generator", "拆解长文为卡片大纲", "article 模式", List.of(), "1", new ByteArrayResource(new byte[0]));

    String summary = formatter.format(List.of(a, b));

    assertThat(summary).contains("- cardflow.html-card-generator: 生成 CardFlow 信息卡");
    assertThat(summary).contains("- cardflow.article-generator: 拆解长文为卡片大纲");
  }

  @Test
  void returnsEmptyStringForEmptyList() {
    assertThat(formatter.format(List.of())).isEmpty();
  }

  @Test
  void sortsAlphabeticallyByName() {
    SkillMeta b = new SkillMeta("cardflow.b-generator", "B", "", List.of(), "1", new ByteArrayResource(new byte[0]));
    SkillMeta a = new SkillMeta("cardflow.a-generator", "A", "", List.of(), "1", new ByteArrayResource(new byte[0]));

    String summary = formatter.format(List.of(b, a));

    assertThat(summary.indexOf("cardflow.a-generator")).isLessThan(summary.indexOf("cardflow.b-generator"));
  }
}