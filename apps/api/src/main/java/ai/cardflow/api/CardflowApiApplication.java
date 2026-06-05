package ai.cardflow.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CardFlow AI 后端应用入口。
 *
 * <p>负责启动 Spring Boot 容器，并加载 API、数据库初始化、渲染和 LLM Provider 等组件。</p>
 */
@SpringBootApplication
public class CardflowApiApplication {
  /**
   * 启动本地后端服务。
   *
   * @param args 命令行启动参数
   */
  public static void main(String[] args) {
    SpringApplication.run(CardflowApiApplication.class, args);
  }
}
