package ai.cardflow.api.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class HtmlCardValidatorTest {
  private final HtmlCardValidator validator = new HtmlCardValidator();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void acceptsValidCard() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><head></head><body></body></html>","designNotes":"","warnings":[]}
        """);
    assertThatCode(() -> validator.validate(node)).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingKind() throws Exception {
    JsonNode node = mapper.readTree("""
        {"title":"x","html":"<!doctype html><html></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("html_card");
  }

  @Test
  void rejectsBlankTitle() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"  ","html":"<!doctype html><html></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("title");
  }

  @Test
  void rejectsBlankHtml() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":""}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("html");
  }

  @Test
  void rejectsHtmlWithoutHtmlTags() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<body>just body</body>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("完整 HTML 文档");
  }

  @Test
  void rejectsScriptTag() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><body><script>alert(1)</script></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("script");
  }

  @Test
  void rejectsExternalUrl() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><body><a href='https://example.com'>x</a></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("外部资源");
  }

  @Test
  void rejectsImportDirective() throws Exception {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><head><style>@import url('foo.css')</style></head><body></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("外部样式资源");
  }
}