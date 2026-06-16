package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.test.web.client.MockRestServiceServer;

class DeepSeekLlmProviderTest {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void failsClearlyWhenApiKeyIsMissing() {
    DeepSeekLlmProvider provider = new DeepSeekLlmProvider(
      properties(""),
      objectMapper,
      new RestTemplate()
    );

    assertThatThrownBy(() -> provider.generateContent(topicRequest()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("DEEPSEEK_API_KEY");
  }

  @Test
  void sendsTopicRequestToDeepSeekJsonMode() {
    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
    DeepSeekLlmProvider provider = new DeepSeekLlmProvider(properties("test-key"), objectMapper, restTemplate);

    server.expect(requestTo("https://api.deepseek.com/chat/completions"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(header("Authorization", "Bearer test-key"))
      .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
      .andExpect(jsonPath("$.max_tokens").value(5000))
      .andExpect(jsonPath("$.response_format.type").value("json_object"))
      .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("布局选择")))
      .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("不允许出现大面积空白")))
      .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("comparison / flow / list / mindmap / quadrant")))
      .andExpect(jsonPath("$.messages[1].role").value("user"))
      .andRespond(withSuccess("""
        {
          "choices": [
            {
              "message": {
                "content": "{\\"kind\\":\\"html_card\\",\\"title\\":\\"真正成长靠行动闭环\\",\\"html\\":\\"<!doctype html><html><head><style>body{margin:0}.card{width:900px;height:1200px}</style></head><body><main class='card'>真正成长靠行动闭环</main></body></html>\\",\\"designNotes\\":\\"问题反转卡\\",\\"warnings\\":[]}"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """, MediaType.APPLICATION_JSON));

    String contentJson = provider.generateContent(topicRequest());

    assertThat(contentJson).contains("\"kind\":\"html_card\"");
    assertThat(contentJson).contains("\"html\"");
    assertThat(contentJson).contains("真正成长靠行动闭环");
    server.verify();
  }

  private GenerateContentRequest topicRequest() {
    return new GenerateContentRequest(
      "topic",
      "xhs_3_4",
      "precise_card",
      "xiaohongshu-highlight",
      new TopicInput("为什么学了很多却还是没成长？", "消费知识不等于成长", "短句化"),
      null
    );
  }

  private AppProperties properties(String apiKey) {
    return new AppProperties(
      new AppProperties.Storage("storage/outputs"),
      new AppProperties.App("local-user"),
      new AppProperties.Llm("deepseek", "https://api.deepseek.com", "deepseek-v4-flash", apiKey, 5000)
    );
  }
}
