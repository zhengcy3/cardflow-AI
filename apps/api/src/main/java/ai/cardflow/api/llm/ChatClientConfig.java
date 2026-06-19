package ai.cardflow.api.llm;

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
 * <p>复用 DeepSeek 的 OpenAI 兼容协议,通过自定义 base-url 接入。
 * baseUrl / apiKey / model / temperature 从 {@code spring.ai.openai.*} 读取。</p>
 */
@Configuration
public class ChatClientConfig {

  @Bean
  public ChatClient chatClient(
    OpenAiApi deepSeekApi,
    SkillRegistry registry,
    ReadSkillTool readSkillTool
  ) {
    OpenAiChatOptions options = OpenAiChatOptions.builder()
      .model("deepseek-chat")
      .temperature(0.85)
      .build();
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
      .openAiApi(deepSeekApi)
      .defaultOptions(options)
      .build();
    return ChatClient.builder(chatModel)
      .defaultAdvisors(new SkillRoutingAdvisor(registry))
      .defaultToolCallbacks(readSkillTool)
      .build();
  }

  @Bean
  public OpenAiApi deepSeekApi(
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.base-url}") String baseUrl,
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.api-key}") String apiKey
  ) {
    return OpenAiApi.builder()
      .baseUrl(baseUrl)
      .apiKey(apiKey)
      .build();
  }
}