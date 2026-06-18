package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SkillMetaTest {
  @Test
  void recordExposesAllFields() {
    ByteArrayResource resource = new ByteArrayResource("body".getBytes());
    SkillMeta meta = new SkillMeta(
      "cardflow.html-card-generator",
      "生成 CardFlow 信息卡",
      "topic 模式生成时",
      List.of("topic", "html_card"),
      "1",
      resource
    );

    assertThat(meta.name()).isEqualTo("cardflow.html-card-generator");
    assertThat(meta.description()).isEqualTo("生成 CardFlow 信息卡");
    assertThat(meta.whenToApply()).isEqualTo("topic 模式生成时");
    assertThat(meta.tags()).containsExactly("topic", "html_card");
    assertThat(meta.version()).isEqualTo("1");
    assertThat(meta.contentResource()).isSameAs(resource);
  }
}