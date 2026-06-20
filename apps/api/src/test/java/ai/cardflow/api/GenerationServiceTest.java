package ai.cardflow.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.database.DatabaseInitializer;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.TopicInput;
import ai.cardflow.api.service.GenerationService;
import ai.cardflow.api.service.HtmlCardValidator;
import ai.cardflow.api.skill.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class GenerationServiceTest {
  @TempDir
  private Path tempDir;

  private GenerationService generationService;
  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    AppProperties properties = new AppProperties(
      new AppProperties.Storage("storage/outputs"),
      new AppProperties.App("local-user")
    );
    DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("cardflow-test.db"));
    dataSource.setDriverClassName("org.sqlite.JDBC");
    jdbc = new JdbcTemplate(dataSource);
    new DatabaseInitializer(jdbc, properties).run(null);

    String validJson = "{\"kind\":\"html_card\",\"title\":\"x\",\"html\":\"<!doctype html><html><head></head><body></body></html>\",\"designNotes\":\"\",\"warnings\":[]}";

    ChatClient chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.content()).thenReturn(validJson);

    SkillRegistry skillRegistry = mock(SkillRegistry.class);
    when(skillRegistry.readFullContent("cardflow.html-card-generator")).thenReturn("""
        # CardFlow html_card 生成协议
        返回 JSON：kind/title/html/designNotes/warnings
        """);

    generationService = new GenerationService(
      jdbc, properties, chatClient, skillRegistry, new HtmlCardValidator(), new ObjectMapper(), "deepseek-v4-flash", 2048
    );
  }

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
    Integer taskCount = jdbc.queryForObject("select count(*) from generate_task where task_type = 'content_generation'", Integer.class);
    Integer usageCount = jdbc.queryForObject("select count(*) from usage_record where model_name = 'deepseek-v4-flash'", Integer.class);
    assertThat(taskCount).isEqualTo(1);
    assertThat(usageCount).isEqualTo(1);
  }
}