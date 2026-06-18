package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.cardflow.api.skill.SkillRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

class SkillRoutingAdvisorTest {

  @Test
  void beforeAppendsSkillSummaryToSystemText() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.summary()).thenReturn("- cardflow.html-card-generator: 生成 CardFlow 信息卡");

    AdvisorChain chain = mock(AdvisorChain.class);
    Prompt prompt = new Prompt(List.of(new SystemMessage("ORIGINAL")));
    ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

    SkillRoutingAdvisor advisor = new SkillRoutingAdvisor(registry);
    ChatClientRequest updated = advisor.before(request, chain);

    assertThat(updated.prompt().getSystemMessage().getText())
      .startsWith("ORIGINAL")
      .contains("可用 Skills")
      .contains("read_skill")
      .contains("- cardflow.html-card-generator: 生成 CardFlow 信息卡");
  }

  @Test
  void afterReturnsResponseUnchanged() {
    SkillRegistry registry = mock(SkillRegistry.class);
    SkillRoutingAdvisor advisor = new SkillRoutingAdvisor(registry);

    assertThat(advisor.after(null, mock(AdvisorChain.class))).isNull();
  }

  @Test
  void getOrderIsZero() {
    assertThat(new SkillRoutingAdvisor(mock(SkillRegistry.class)).getOrder()).isEqualTo(0);
  }
}