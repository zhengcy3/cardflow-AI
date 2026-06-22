package ai.cardflow.api.llm;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
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
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}") String model,
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.temperature:0.85}") double temperature,
    @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.chat.options.max-tokens:4096}") int maxTokens
  ) {
    OpenAiChatOptions options = OpenAiChatOptions.builder()
      .model(model)
      .temperature(temperature)
      .maxTokens(maxTokens)
      .responseFormat(ResponseFormat.builder()
        .type(ResponseFormat.Type.JSON_OBJECT)
        .build())
      .build();
    OpenAiChatModel chatModel = OpenAiChatModel.builder()
      .openAiApi(deepSeekApi)
      .defaultOptions(options)
      .build();
    ChatModel observingModel = new LoggingChatModel(chatModel, model);
    return ChatClient.builder(observingModel).build();
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