package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.cardflow.api.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

class ReadSkillToolTest {

  @Test
  void returnsFullContentForKnownSkill() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.readFullContent("cardflow.html-card-generator")).thenReturn("# body");

    String result = new ReadSkillTool(registry).call("{\"name\":\"cardflow.html-card-generator\"}");

    assertThat(result).isEqualTo("# body");
  }

  @Test
  void returnsErrorJsonForUnknownSkill() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.readFullContent("nope"))
      .thenThrow(new IllegalArgumentException("Unknown skill: nope"));

    String result = new ReadSkillTool(registry).call("{\"name\":\"nope\"}");

    assertThat(result).contains("\"error\"");
    assertThat(result).contains("Unknown skill");
  }

  @Test
  void returnsErrorJsonForMalformedArguments() {
    String result = new ReadSkillTool(mock(SkillRegistry.class)).call("not json");
    assertThat(result).contains("\"error\"");
  }

  @Test
  void returnsErrorJsonWhenNameFieldMissing() {
    String result = new ReadSkillTool(mock(SkillRegistry.class)).call("{\"other\":\"value\"}");
    assertThat(result).contains("\"error\"");
  }

  @Test
  void exposesToolDefinition() {
    ReadSkillTool tool = new ReadSkillTool(mock(SkillRegistry.class));
    assertThat(tool.getToolDefinition().name()).isEqualTo("read_skill");
    assertThat(tool.getToolDefinition().inputSchema()).contains("\"name\"");
    assertThat(tool.getToolDefinition().description()).contains("Skill");
  }
}