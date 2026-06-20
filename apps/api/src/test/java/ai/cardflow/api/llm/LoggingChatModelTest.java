package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class LoggingChatModelTest {
  @AfterEach
  void tearDown() {
    LlmCallContext.clear();
  }

  @Test
  void recordsApiRoundAndTokens() {
    ChatModel delegate = mock(ChatModel.class);
    ChatResponse response = ChatResponse.builder()
      .generations(java.util.List.of(new Generation(null)))
      .metadata(ChatResponseMetadata.builder().usage(new DefaultUsage(100, 200)).build())
      .build();
    when(delegate.call(any(Prompt.class))).thenReturn(response);

    LlmCallContext.begin("task-1");
    new LoggingChatModel(delegate, "deepseek-chat").call(new Prompt("hello"));

    LlmCallStats stats = LlmCallContext.snapshot();
    assertThat(stats.callCount()).isEqualTo(1);
    assertThat(stats.promptTokens()).isEqualTo(100);
    assertThat(stats.completionTokens()).isEqualTo(200);
  }
}
