package ai.cardflow.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用级配置入口。
 *
 * <p>当前只负责启用 {@link AppProperties}，让 application.yml 中的 cardflow 配置可以注入到业务组件。</p>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {
}