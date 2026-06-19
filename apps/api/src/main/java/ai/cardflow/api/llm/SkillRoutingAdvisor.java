package ai.cardflow.api.llm;

import ai.cardflow.api.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 把当前可用 Skill 摘要拼到 system prompt 末尾。
 *
 * <p>每次请求都会执行,LLM 在 system 提示中看到 Skill 列表,
 * 详细规则仍需通过 {@code read_skill} 工具按需读取。</p>
 */
@Component
public class SkillRoutingAdvisor implements BaseAdvisor {
  private static final String SKILLS_HEADER = """

      ## 可用 Skills

      以下 Skills 可用。需要详细规则时,调用 read_skill 工具读取完整 SKILL.md。

      %s

      """;

  private final SkillRegistry registry;

  public SkillRoutingAdvisor(SkillRegistry registry) {
    this.registry = registry;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    String summary = registry.summary();
    Prompt prompt = request.prompt();
    String currentSystem = prompt.getSystemMessage().getText();
    String augmented = currentSystem + SKILLS_HEADER.formatted(summary);
    return request.mutate()
      .prompt(prompt.augmentSystemMessage(augmented))
      .build();
  }

  @Override
  public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
    return response;
  }
}