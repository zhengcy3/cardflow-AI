package ai.cardflow.api.exception;

/**
 * 将内部异常转换为面向用户的中文提示，避免堆栈和原始 JSON 泄露到前端。
 */
public final class ApiErrorMessages {
  private ApiErrorMessages() {}

  /**
   * 从异常链提取并转换用户可读消息。
   *
   * @param throwable 原始异常
   * @return 用户可读错误文案
   */
  public static String toUserMessage(Throwable throwable) {
    if (throwable == null) {
      return defaultMessage();
    }
    return toUserMessage(extractRawMessage(throwable));
  }

  /**
   * 将原始错误文本转换为用户可读消息。
   *
   * @param raw 原始错误文本
   * @return 用户可读错误文案
   */
  public static String toUserMessage(String raw) {
    if (raw == null || raw.isBlank()) {
      return defaultMessage();
    }

    String normalized = raw.trim();
    if (normalized.contains("\n\tat ") || normalized.contains("Caused by:")) {
      normalized = normalized.split("\n")[0].trim();
    }

    String lower = normalized.toLowerCase();

    if (looksLikeStructuredPayload(normalized)) {
      return mapStructuredPayload(lower);
    }

    if (lower.contains("authentication") || lower.contains("api key") || lower.contains("401")) {
      return "AI 服务认证失败，请检查 DEEPSEEK_API_KEY 是否配置正确。";
    }
    if (lower.contains("rate limit") || lower.contains("quota") || lower.contains("429")) {
      return "AI 服务请求过于频繁或额度不足，请稍后再试。";
    }
    if (lower.contains("timeout") || lower.contains("timed out")) {
      return "AI 服务响应超时，请稍后重试。";
    }
    if (lower.contains("playwright") || lower.contains("chromium")) {
      return "图片渲染失败，请确认已安装 Playwright Chromium。";
    }
    if (lower.contains("failed to render png")) {
      return "图片渲染失败，请稍后重试。";
    }
    if (lower.contains("failed to serialize json")) {
      return "内容处理失败，请稍后重试。";
    }
    if (normalized.contains("html_card") || normalized.contains("DeepSeek 返回")) {
      return normalized;
    }

    if (normalized.length() > 160) {
      return defaultMessage();
    }

    return normalized;
  }

  private static String extractRawMessage(Throwable throwable) {
    Throwable current = throwable;
    String message = throwable.getMessage();
    while (current.getCause() != null) {
      current = current.getCause();
      if (current.getMessage() != null && !current.getMessage().isBlank()) {
        message = current.getMessage();
      }
    }
    return message;
  }

  private static boolean looksLikeStructuredPayload(String value) {
    return value.startsWith("{") || value.startsWith("[") || value.contains("\"error\"");
  }

  private static String mapStructuredPayload(String lower) {
    if (lower.contains("authentication") || lower.contains("api key") || lower.contains("401")) {
      return "AI 服务认证失败，请检查 DEEPSEEK_API_KEY 是否配置正确。";
    }
    if (lower.contains("rate limit") || lower.contains("quota") || lower.contains("429")) {
      return "AI 服务请求过于频繁或额度不足，请稍后再试。";
    }
    return defaultMessage();
  }

  private static String defaultMessage() {
    return "操作失败，请稍后重试。";
  }
}
