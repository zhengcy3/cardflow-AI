package ai.cardflow.api.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiErrorMessagesTest {
  @Test
  void mapsAuthenticationJsonToFriendlyMessage() {
    String raw = "401 - {\"error\":{\"message\":\"Authentication Fails, Your api key: ****07ee is invalid\"}}";
    assertThat(ApiErrorMessages.toUserMessage(raw))
      .isEqualTo("AI 服务认证失败，请检查 DEEPSEEK_API_KEY 是否配置正确。");
  }

  @Test
  void stripsStackTracePrefix() {
    String raw = "401 unauthorized\n\tat com.example.Demo.main(Demo.java:12)";
    assertThat(ApiErrorMessages.toUserMessage(raw))
      .isEqualTo("AI 服务认证失败，请检查 DEEPSEEK_API_KEY 是否配置正确。");
  }

  @Test
  void keepsValidatorMessages() {
    assertThat(ApiErrorMessages.toUserMessage("DeepSeek 返回的 title 不能为空。"))
      .isEqualTo("DeepSeek 返回的 title 不能为空。");
  }
}
