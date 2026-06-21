package ai.cardflow.api.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.cardflow.api.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MiniMaxImageProviderTest {
  private MockRestServiceServer server;
  private MiniMaxImageProvider provider;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("https://api.minimaxi.com");
    server = MockRestServiceServer.bindTo(builder).build();
    AppProperties properties = new AppProperties(
      new AppProperties.Storage("storage/outputs"),
      new AppProperties.App("local-user"),
      new AppProperties.Image("minimax", new AppProperties.MiniMaxImage(
        "https://api.minimaxi.com", "test-key", "image-01", false
      ))
    );
    provider = new MiniMaxImageProvider(properties, new ObjectMapper(), builder);
  }

  @Test
  void decodesSuccessfulBase64Image() {
    String encoded = Base64.getEncoder().encodeToString(new byte[] {1, 2, 3});
    server.expect(request -> {
      assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
      assertThat(request.getURI().getPath()).isEqualTo("/v1/image_generation");
    }).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("""
        {
          "data": { "image_base64": ["%s"] },
          "base_resp": { "status_code": 0, "status_msg": "success" }
        }
        """.formatted(encoded), MediaType.APPLICATION_JSON));

    byte[] image = provider.generate(new ImageGenerationRequest("cover art", "3:4"));
    assertThat(image).containsExactly(1, 2, 3);
    server.verify();
  }

  @Test
  void mapsAuthFailureToUserMessage() {
    server.expect(request -> {
      assertThat(request.getMethod()).isEqualTo(HttpMethod.POST);
      assertThat(request.getURI().getPath()).isEqualTo("/v1/image_generation");
    }).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("""
        { "base_resp": { "status_code": 2049, "status_msg": "invalid api key" } }
        """, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> provider.generate(new ImageGenerationRequest("cover art", "3:4")))
      .hasMessageContaining("API Key");
    server.verify();
  }
}
