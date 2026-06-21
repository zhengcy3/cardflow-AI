package ai.cardflow.api.image;

import ai.cardflow.api.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * MiniMax image-01 文生图 Provider。
 *
 * @see <a href="https://platform.minimaxi.com/docs/api-reference/image-generation-t2i">MiniMax 文生图 API</a>
 */
@Service
public class MiniMaxImageProvider implements ImageProvider {
  private static final Logger log = LoggerFactory.getLogger(MiniMaxImageProvider.class);

  private final AppProperties.MiniMaxImage config;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  public MiniMaxImageProvider(AppProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
    this.config = properties.image().minimax();
    this.objectMapper = objectMapper;
    this.restClient = restClientBuilder
      .baseUrl(config.baseUrl())
      .defaultHeader("Authorization", "Bearer " + config.apiKey())
      .build();
  }

  @Override
  public byte[] generate(ImageGenerationRequest request) {
    if (request.prompt() == null || request.prompt().isBlank()) {
      throw new IllegalStateException("生图提示词不能为空。");
    }

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", config.model());
    payload.put("prompt", request.prompt());
    payload.put("aspect_ratio", request.aspectRatio());
    payload.put("response_format", "base64");
    payload.put("n", 1);
    payload.put("prompt_optimizer", config.promptOptimizer());

    log.info("MiniMax image generation started model={} aspectRatio={} promptLength={}",
      config.model(), request.aspectRatio(), request.prompt().length());

    String responseBody = restClient.post()
      .uri("/v1/image_generation")
      .contentType(MediaType.APPLICATION_JSON)
      .body(payload)
      .retrieve()
      .body(String.class);

    return decodeImage(responseBody);
  }

  private byte[] decodeImage(String responseBody) {
    try {
      JsonNode root = objectMapper.readTree(responseBody);
      assertSuccess(root.path("base_resp"));

      JsonNode images = root.path("data").path("image_base64");
      if (!images.isArray() || images.isEmpty()) {
        throw new IllegalStateException("MiniMax 未返回图片数据。");
      }

      String encoded = images.get(0).asText("");
      if (encoded.isBlank()) {
        throw new IllegalStateException("MiniMax 返回的图片内容为空。");
      }
      return Base64.getDecoder().decode(encoded);
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("MiniMax 生图响应解析失败。", e);
    }
  }

  private void assertSuccess(JsonNode baseResp) {
    int statusCode = baseResp.path("status_code").asInt(-1);
    if (statusCode == 0) {
      return;
    }
    String statusMsg = baseResp.path("status_msg").asText("unknown");
    throw new IllegalStateException(mapStatusCode(statusCode, statusMsg));
  }

  private static String mapStatusCode(int statusCode, String statusMsg) {
    return switch (statusCode) {
      case 1002 -> "MiniMax 请求过于频繁，请稍后再试。";
      case 1004, 2049 -> "MiniMax API Key 配置错误或已失效。";
      case 1008 -> "MiniMax 账户余额不足，请充值后再试。";
      case 1026 -> "生图提示词包含敏感内容，请修改主题后重试。";
      case 2013 -> "MiniMax 生图参数无效，请检查输出比例和提示词。";
      default -> "MiniMax 生图失败（" + statusCode + "：" + statusMsg + "）。";
    };
  }
}
