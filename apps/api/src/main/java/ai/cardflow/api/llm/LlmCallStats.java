package ai.cardflow.api.llm;

/**
 * 单次内容生成过程中累积的 LLM 调用统计。
 *
 * @param taskId 关联的 generate_task ID
 * @param callCount 实际打到 DeepSeek 的 API 轮次（含 tool calling 多轮）
 * @param promptTokens 累计输入 token
 * @param completionTokens 累计输出 token
 */
public record LlmCallStats(String taskId, int callCount, int promptTokens, int completionTokens) {
  public int totalTokens() {
    return promptTokens + completionTokens;
  }
}
