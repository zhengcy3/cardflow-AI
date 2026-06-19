package ai.cardflow.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级配置入口。
 *
 * <p>负责启用 {@link AppProperties} 和注册 Skill 加载所需的 YAML ObjectMapper。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

  /**
   * 用于解析 Skill meta.yaml 的 YAML ObjectMapper。
   *
   * <p>Spring 默认只注册 JSON ObjectMapper;YAML 解析需要单独的 bean。</p>
   *
   * @return 配置了 YAMLFactory 的 ObjectMapper
   */
  @Bean
  public ObjectMapper yamlObjectMapper() {
    return new ObjectMapper(new YAMLFactory());
  }
}