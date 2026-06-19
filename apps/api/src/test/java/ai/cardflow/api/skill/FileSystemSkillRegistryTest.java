package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class FileSystemSkillRegistryTest {
  @TempDir
  Path tempDir;

  private FileSystemSkillRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectory(tempDir.resolve("skills"));
    Path skillDir = Files.createDirectory(tempDir.resolve("skills/cardflow.html-card-generator"));
    Files.writeString(skillDir.resolve("meta.yaml"), """
        name: cardflow.html-card-generator
        description: 生成 CardFlow 信息卡
        whenToApply: topic 模式
        tags:
          - topic
        version: 1
        """);
    Files.writeString(skillDir.resolve("SKILL.md"), "# body\n");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    registry = new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", tempDir.toString());
  }

  @Test
  void loadsAllMetadata() {
    assertThat(registry.all()).hasSize(1);
    SkillMeta meta = registry.all().get(0);
    assertThat(meta.name()).isEqualTo("cardflow.html-card-generator");
    assertThat(meta.description()).isEqualTo("生成 CardFlow 信息卡");
    assertThat(meta.whenToApply()).isEqualTo("topic 模式");
    assertThat(meta.tags()).containsExactly("topic");
    assertThat(meta.version()).isEqualTo("1");
  }

  @Test
  void findsByName() {
    assertThat(registry.find("cardflow.html-card-generator")).isPresent();
    assertThat(registry.find("unknown")).isEmpty();
  }

  @Test
  void readsFullSkillContent() {
    String content = registry.readFullContent("cardflow.html-card-generator");
    assertThat(content).isEqualTo("# body\n");
  }

  @Test
  void readFullContentThrowsOnUnknown() {
    assertThatThrownBy(() -> registry.readFullContent("nope"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unknown skill: nope");
  }

  @Test
  void summaryIsNonEmpty() {
    assertThat(registry.summary()).contains("cardflow.html-card-generator");
  }

  @Test
  void failsWhenNameFormatIsInvalid(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path bad = Files.createDirectory(otherTemp.resolve("skills/BadName"));
    Files.writeString(bad.resolve("meta.yaml"), """
        name: BadName
        description: x
        whenToApply: y
        version: 1
        """);
    Files.writeString(bad.resolve("SKILL.md"), "body");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Invalid skill name");
  }

  @Test
  void failsWhenDirectoryNameDoesNotMatch(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path skill = Files.createDirectory(otherTemp.resolve("skills/wrong-dir"));
    Files.writeString(skill.resolve("meta.yaml"), """
        name: cardflow.html-card-generator
        description: x
        whenToApply: y
        version: 1
        """);
    Files.writeString(skill.resolve("SKILL.md"), "body");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("does not match directory");
  }

  @Test
  void failsWhenSkillMdMissing(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path skill = Files.createDirectory(otherTemp.resolve("skills/cardflow.test"));
    Files.writeString(skill.resolve("meta.yaml"), """
        name: cardflow.test
        description: x
        whenToApply: y
        version: 1
        """);

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Missing SKILL.md");
  }

  @Test
  void failsWhenNameDuplicate(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    for (String dir : new String[]{"cardflow.dup", "cardflow.dup-other"}) {
      Path skill = Files.createDirectory(otherTemp.resolve("skills/" + dir));
      Files.writeString(skill.resolve("meta.yaml"), """
          name: cardflow.dup
          description: x
          whenToApply: y
          version: 1
          """);
      Files.writeString(skill.resolve("SKILL.md"), "body");
    }

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Duplicate skill name");
  }
}