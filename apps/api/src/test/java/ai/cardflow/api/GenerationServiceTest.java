package ai.cardflow.api;

import static org.assertj.core.api.Assertions.assertThat;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.database.DatabaseInitializer;
import ai.cardflow.api.llm.LlmProvider;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import ai.cardflow.api.service.GenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 内容生成服务集成测试。
 *
 * <p>通过测试专用 Provider 验证数据库初始化和生成任务记录可以协同工作。</p>
 */
class GenerationServiceTest {
  @TempDir
  private Path tempDir;

  private GenerationService generationService;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    AppProperties properties = new AppProperties(
      new AppProperties.Storage("storage/outputs"),
      new AppProperties.App("local-user"),
      new AppProperties.Llm("deepseek", "https://api.deepseek.com", "deepseek-v4-flash", "test-key", 5000)
    );
    DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("cardflow-test.db"));
    dataSource.setDriverClassName("org.sqlite.JDBC");
    jdbc = new JdbcTemplate(dataSource);
    new DatabaseInitializer(jdbc, properties).run(null);
    generationService = new GenerationService(jdbc, properties, new ObjectMapper(), new TestLlmProvider());
  }

  /**
   * 验证主题模式可以生成单卡结构 JSON。
   */
  @Test
  void generatesSingleCardJsonForTopicMode() {
    var response = generationService.generate(new GenerateContentRequest(
      "topic",
      "xhs_3_4",
      "precise_card",
      "xiaohongshu-highlight",
      new TopicInput("为什么学了很多却还是没成长？", "消费知识不等于成长", "短句化"),
      null
    ));

    assertThat(response.taskId()).isNotBlank();
    assertThat(response.contentJson()).contains("\"kind\":\"html_card\"");
    assertThat(response.contentJson()).contains("为什么学了很多却还是没成长");
    Integer taskCount = jdbc.queryForObject("select count(*) from generate_task where task_type = 'content_generation'", Integer.class);
    Integer usageCount = jdbc.queryForObject("select count(*) from usage_record where model_name = 'test-provider'", Integer.class);
    assertThat(taskCount).isEqualTo(1);
    assertThat(usageCount).isEqualTo(1);
  }

  private static final class TestLlmProvider implements LlmProvider {
    @Override
    public String name() {
      return "test-provider";
    }

    @Override
    public String generateContent(GenerateContentRequest request) {
      return """
        {"kind":"html_card","title":"为什么学了很多却还是没成长？","html":"<!doctype html><html><head><style>body{margin:0}.card{width:900px;height:1200px}</style></head><body><main class='card'>为什么学了很多却还是没成长？</main></body></html>","designNotes":"test","warnings":[]}
        """;
    }
  }
}
