package ai.cardflow.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 应用级配置入口。
 *
 * <p>当前只负责启用 {@link AppProperties}，让 application.yml 中的 cardflow 配置可以注入到业务组件。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
  /**
   * DeepSeek API HTTP 客户端。
   *
   * @return RestTemplate 实例
   */
  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }
}
