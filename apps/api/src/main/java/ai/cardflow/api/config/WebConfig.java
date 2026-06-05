package ai.cardflow.api.config;

import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层基础配置。
 *
 * <p>集中配置前端开发环境跨域，以及把本地 PNG 输出目录暴露成静态资源。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
  private final AppProperties properties;

  /**
   * 注入应用配置，用于读取本地输出目录。
   *
   * @param properties cardflow.* 配置
   */
  public WebConfig(AppProperties properties) {
    this.properties = properties;
  }

  /**
   * 允许 Vite 开发服务器访问后端 API。
   *
   * @param registry Spring MVC 跨域配置注册器
   */
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
      .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
      .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
      .allowedHeaders("*");
  }

  /**
   * 将 storage/outputs 映射为 /outputs/**，供前端直接预览或下载生成图片。
   *
   * @param registry Spring MVC 静态资源注册器
   */
  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // normalize 避免配置中的相对路径或多余路径段影响静态资源定位。
    String location = Path.of(properties.storage().outputDir()).normalize().toUri().toString();
    registry.addResourceHandler("/outputs/**").addResourceLocations(location);
  }
}
