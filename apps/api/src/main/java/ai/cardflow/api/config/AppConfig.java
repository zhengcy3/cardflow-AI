package ai.cardflow.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

/**
 * 应用级配置入口。
 *
 * <p>启用 {@link AppProperties} 并提供 Skill 加载所需的 YAML 解析工具。</p>
 *
 * <p><b>为什么不引入 jackson-dataformat-yaml</b>:Spring Framework
 * {@code spring-web} 检测到 classpath 有 {@code jackson-dataformat-yaml} 时会
 * 自动注册 {@code MappingJackson2YamlHttpMessageConverter}。该 converter 的
 * supports() 会把 {@code Accept: application/json} 误判为可处理,导致 JSON 响应
 * 被错误地以 YAML 序列化输出(response body 头部多出 {@code ---} 前缀),前端
 * fetch 解析 JSON 直接失败。Spring 的 YAML converter 检测只针对 Jackson YAML,不会
 * 监听 snakeyaml,所以换成 snakeyaml 后 HTTP 层不再有干扰。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

  /**
   * 解析 Skill meta.yaml 的轻量 YAML reader。
   *
   * <p>Skill 加载在启动期只跑一次,不需要 Jackson 的全套 ObjectMapper 能力,
   * 用 SnakeYAML 直接读 Map 更直接。</p>
   *
   * @return 解析单个 YAML 文档为 {@code Map<String, Object>}
   */
  @Bean
  public Yaml yamlReader() {
    return new Yaml();
  }
}