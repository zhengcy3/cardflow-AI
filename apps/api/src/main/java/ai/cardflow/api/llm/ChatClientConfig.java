package ai.cardflow.api.llm;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 装配。
 *
 * <p>复用 DeepSeek 的 OpenAI 兼容协议,通过自定义 base-url 接入。</p>
 */
@Configuration
public class ChatClientConfig {

  @Bean
  public ChatClient chatClient(
    OpenAiApi deepSeekApi,
    SkillRegistry registry,
    ReadSkillTool readSkillTool
  ) {
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
      .openAiApi(deepSeekApi)
      .defaultOptions(OpenAiChatOptions.builder()
        .model("deepseek-chat")
        .temperature(0.85)
        .build())
      .build();
    return ChatClient.builder(chatModel)
      .defaultAdvisors(new SkillRoutingAdvisor(registry))
      .defaultToolCallbacks(readSkillTool)
      .build();
  }

  @Bean
  public OpenAiApi deepSeekApi(AppProperties properties) {
    AppProperties.Llm llm = properties.llm();
    return OpenAiApi.builder()
      .baseUrl(llm.baseUrl())
      .apiKey(llm.apiKey())
      .build();
  }
}