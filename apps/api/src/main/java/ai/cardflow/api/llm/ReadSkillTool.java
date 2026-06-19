package ai.cardflow.api.llm;

import ai.cardflow.api.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;

/**
 * LLM 可调用的 read_skill 工具,按需读取某个 Skill 的完整 SKILL.md。
 *
 * <p>失败(未知 Skill、参数错误)返回 error JSON 而不抛异常,
 * 让 LLM 在下一轮看到错误并自行恢复。</p>
 */
@Component
public class ReadSkillTool implements ToolCallback {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SCHEMA = """
      {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "description": "Skill 名称,例如 cardflow.html-card-generator"
          }
        },
        "required": ["name"]
      }
      """;

  private final SkillRegistry registry;
  private final ToolDefinition toolDefinition;

  public ReadSkillTool(SkillRegistry registry) {
    this.registry = registry;
    this.toolDefinition = DefaultToolDefinition.builder()
      .name("read_skill")
      .description("按需读取某个 Skill 的完整 SKILL.md 内容,获取详细的协议/约束/风格规则")
      .inputSchema(SCHEMA)
      .build();
  }

  @Override
  public ToolDefinition getToolDefinition() {
    return toolDefinition;
  }

  @Override
  public String call(String jsonArguments) {
    try {
      JsonNode args = MAPPER.readTree(jsonArguments);
      JsonNode nameNode = args.get("name");
      if (nameNode == null || !nameNode.isTextual()) {
        return errorJson("Invalid arguments: missing 'name' field");
      }
      return registry.readFullContent(nameNode.asText());
    } catch (Exception e) {
      return errorJson(e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage()));
    }
  }

  private static String errorJson(String detail) {
    return "{\"error\":\"" + detail.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
  }
}