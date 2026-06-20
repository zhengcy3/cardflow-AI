package ai.cardflow.api.llm;

/**
 * 线程级 LLM 调用计数器，供 {@link LoggingChatModel} 写入、{@link ai.cardflow.api.service.GenerationService} 读取。
 */
public final class LlmCallContext {
  private static final ThreadLocal<LlmCallStats> STATS = new ThreadLocal<>();

  private LlmCallContext() {}

  public static void begin(String taskId) {
    STATS.set(new LlmCallStats(taskId, 0, 0, 0));
  }

  public static void recordCall(int promptTokens, int completionTokens) {
    LlmCallStats current = STATS.get();
    if (current == null) {
      return;
    }
    STATS.set(new LlmCallStats(
      current.taskId(),
      current.callCount() + 1,
      current.promptTokens() + promptTokens,
      current.completionTokens() + completionTokens
    ));
  }

  public static LlmCallStats snapshot() {
    return STATS.get();
  }

  public static void clear() {
    STATS.remove();
  }
}
