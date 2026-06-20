package ai.cardflow.api.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/**
 * 包装 {@link ChatModel}，记录每次 API 轮次与 token 用量。
 */
public class LoggingChatModel implements ChatModel {
  private static final Logger log = LoggerFactory.getLogger(LoggingChatModel.class);

  private final ChatModel delegate;
  private final String modelName;

  public LoggingChatModel(ChatModel delegate, String modelName) {
    this.delegate = delegate;
    this.modelName = modelName;
  }

  @Override
  public ChatResponse call(Prompt prompt) {
    ChatResponse response = delegate.call(prompt);
    Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
    int promptTokens = tokenCount(usage == null ? null : usage.getPromptTokens());
    int completionTokens = tokenCount(usage == null ? null : usage.getCompletionTokens());
    LlmCallContext.recordCall(promptTokens, completionTokens);

    LlmCallStats stats = LlmCallContext.snapshot();
    int round = stats == null ? 1 : stats.callCount();
    String responseModel = response.getMetadata() == null ? modelName : response.getMetadata().getModel();
    log.info(
      "DeepSeek API call taskId={} round={} requestModel={} responseModel={} promptTokens={} completionTokens={} totalTokens={}",
      stats == null ? "-" : stats.taskId(),
      round,
      modelName,
      responseModel == null ? "-" : responseModel,
      promptTokens,
      completionTokens,
      promptTokens + completionTokens
    );
    return response;
  }

  @Override
  public Flux<ChatResponse> stream(Prompt prompt) {
    return delegate.stream(prompt);
  }

  @Override
  public ChatOptions getDefaultOptions() {
    return delegate.getDefaultOptions();
  }

  private static int tokenCount(Integer value) {
    return value == null ? 0 : value;
  }
}
