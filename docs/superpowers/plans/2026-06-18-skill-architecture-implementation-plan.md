# Skill Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded DeepSeek prompt in `DeepSeekLlmProvider` with a Skill-based architecture built on Spring AI (`ChatClient` + `BaseAdvisor` + `ToolCallback`), externalizing prompt content into versionable `SKILL.md` files.

**Architecture:** Three layers — (1) domain layer with `SkillRegistry` scanning `classpath:skills/**/meta.yaml` + `SKILL.md`, (2) Spring AI adapter with `SkillRoutingAdvisor` injecting Skills summaries and `ReadSkillTool` letting the LLM load full Skill content on demand, (3) refactored `GenerationService` using `ChatClient` directly (replacing `LlmProvider`/`DeepSeekLlmProvider`). Backward-compatible: HTTP API contracts and DB schema unchanged.

**Tech Stack:** Spring Boot 3.5.x, Spring AI 1.1.x (latest stable), Jackson YAML, JUnit 5 + AssertJ, Maven.

## Global Constraints

- Spring Boot 3.5.x; Java 17; Maven
- Spring AI 1.1.x (latest stable in 1.1 line)
- Skill names MUST match `^[a-z0-9]+(\.[a-z0-9-]+)+$`
- Skill names MUST match `^[a-z0-9]+(\.[a-z0-9-]+)+$`
- Skill directory name MUST equal `meta.yaml.name`
- All Skills MUST be loaded at startup; failure to load is fail-fast (`IllegalStateException`)
- `ReadSkillTool` MUST return error JSON on failure, never throw
- `HtmlCardValidator` MUST reject `kind != html_card`, blank `title`/`html`, HTML missing `<html>`, HTML containing `<script>`, `http://`, `https://`, `src=`, `href=`, `@import`, `url(`
- Test coverage ≥ 95% on new/modified classes
- HTTP API contract unchanged
- DB schema unchanged
- `model_name` written to `usage_record` is `deepseek-chat`

---

## File Structure

### New files

```
apps/api/src/main/resources/skills/cardflow.html-card-generator/
├── SKILL.md                                  # 7 sections concatenated
└── meta.yaml                                 # name/description/whenToApply/tags/version

apps/api/src/main/java/ai/cardflow/api/skill/
├── SkillMeta.java                            # record
├── SkillRegistry.java                        # interface
├── FileSystemSkillRegistry.java              # @Component, scans classpath
└── SkillSummaryFormatter.java                # bullet-list formatter

apps/api/src/main/java/ai/cardflow/api/llm/
├── SkillRoutingAdvisor.java                  # implements BaseAdvisor
├── ReadSkillTool.java                        # implements ToolCallback
└── ChatClientConfig.java                     # @Configuration, ChatClient bean

apps/api/src/main/java/ai/cardflow/api/service/
└── HtmlCardValidator.java                    # extracted from DeepSeekLlmProvider

apps/api/src/test/java/ai/cardflow/api/skill/
├── FileSystemSkillRegistryTest.java
├── SkillSummaryFormatterTest.java
└── SkillContentSanityTest.java

apps/api/src/test/java/ai/cardflow/api/llm/
├── SkillRoutingAdvisorTest.java
└── ReadSkillToolTest.java

apps/api/src/test/java/ai/cardflow/api/service/
└── HtmlCardValidatorTest.java
```

### Modified files

```
apps/api/pom.xml                             # add Spring AI BOM + openai starter + jackson-dataformat-yaml
apps/api/src/main/resources/application.yml  # add spring.ai.openai block
apps/api/src/main/java/ai/cardflow/api/service/GenerationService.java
apps/api/src/test/java/ai/cardflow/api/GenerationServiceTest.java
```

### Deleted files

```
apps/api/src/main/java/ai/cardflow/api/llm/LlmProvider.java
apps/api/src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java
apps/api/src/test/java/ai/cardflow/api/llm/DeepSeekLlmProviderTest.java
```

---

## Task 0: Upgrade Spring Boot 3.3.5 → 3.5.x

**Files:**
- Modify: `apps/api/pom.xml`

**Interfaces:**
- Consumes: Spring Boot 3.3.5 (current)
- Produces: Spring Boot 3.5.x (latest stable on Maven Central); existing test suite still passes

Spring AI 1.1.x requires Spring Boot 3.4.x or 3.5.x. We pick 3.5.x for the latest patch. This task upgrades Spring Boot first, so Task 1 can confidently use the latest Spring AI 1.1.x.

### Step 0.1: Find the latest Spring Boot 3.5.x patch version

Run:
```bash
curl -s "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-parent/" \
  | grep -oE 'href="3\.5\.[0-9]+/"' | sort -u
```

Pick the **highest 3.5.x** (e.g. `3.5.0` or later patch). Write the chosen version to use in Step 0.2.

### Step 0.2: Edit `apps/api/pom.xml`

Change the parent version:

```xml
<parent>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-parent</artifactId>
  <version>3.5.0</version>  <!-- replace 3.3.5 with the version chosen in Step 0.1 -->
  <relativePath />
</parent>
```

### Step 0.3: Verify dependencies resolve and compile

Run: `cd apps/api && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS. Spring Boot 3.5.x may pull newer transitive dependencies — if any version conflict is reported, surface it to the user before continuing.

### Step 0.4: Run existing test suite

Run: `cd apps/api && mvn -q test`
Expected: PASS for all existing tests.

Common Spring Boot 3.4/3.5 deprecations to watch for:
- `@MockBean` → `@MockitoBean` (since Spring Boot 3.4)
- `@SpyBean` → `@MockitoSpyBean` (since Spring Boot 3.4)
- `RestTemplate` API: still present, no breaking changes in 3.5.x

If any test fails due to API drift, apply the standard 3.4/3.5 migration pattern for that test and commit the fix in this task.

### Step 0.5: Commit

```bash
git add apps/api/pom.xml
git commit -m "build: upgrade Spring Boot 3.3.5 to 3.5.x"
```

---

## Task 1: Add Spring AI dependencies

**Files:**
- Modify: `apps/api/pom.xml`
- Modify: `apps/api/src/main/resources/application.yml`
- Test: existing test suite must still pass

**Interfaces:**
- Consumes: Spring Boot 3.5.x from Task 0
- Produces: `spring-ai-openai` on classpath; `spring.ai.openai` config block; existing tests pass

### Step 1.1: Find the latest Spring AI 1.1.x patch version

Run:
```bash
curl -s "https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/" \
  | grep -oE 'href="1\.1\.[0-9]+/"' | sort -u
```

Pick the **highest 1.1.x** (e.g. `1.1.0` or later patch). Write it down for Step 1.2. Do NOT pick 2.x — that requires Spring Boot 4.x.

### Step 1.2: Edit `apps/api/pom.xml`

Add Spring AI BOM in `<dependencyManagement>` and the openai starter + jackson-dataformat-yaml in `<dependencies>`:

```xml
<properties>
  <java.version>17</java.version>
  <spring-ai.version>1.1.0</spring-ai.version>  <!-- use the version verified in Step 1.1 -->
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <!-- existing deps -->

  <dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai</artifactId>
  </dependency>

  <dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
  </dependency>
</dependencies>
```

- [x] **Step 1.3: Verify pom changes compile**

Run: `cd apps/api && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS. Spring AI classes resolve.

- [ ] **Step 1.4: Add Spring AI config to application.yml**

Edit `apps/api/src/main/resources/application.yml`, append:

```yaml
spring:
  ai:
    openai:
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
          temperature: 0.85
```

The existing `cardflow.llm.*` block stays unchanged for now (still consumed by `DeepSeekLlmProvider` until Task 6 deletes it).

- [ ] **Step 1.5: Verify application boots**

Run: `cd apps/api && mvn -q test -Dtest=GenerationServiceTest`
Expected: PASS. Existing `TestLlmProvider` still works because `ChatClient` is not yet wired anywhere.

If Spring fails to start because it tries to instantiate a `ChatClient` bean automatically — that's expected because `spring-ai-openai-spring-boot-starter` autoconfigures one. Disable autoconfiguration in `CardflowApiApplication.java` for now by adding `@SpringBootApplication(exclude = {org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class})`. This exclusion will be removed in Task 6 when we wire our own `ChatClient` bean.

- [ ] **Step 1.6: Commit**

```bash
git add apps/api/pom.xml apps/api/src/main/resources/application.yml apps/api/src/main/java/ai/cardflow/api/CardflowApiApplication.java
git commit -m "build: add Spring AI dependencies and config"
```

---

## Task 2: Create Skill directory and SKILL.md

**Files:**
- Create: `apps/api/src/main/resources/skills/cardflow.html-card-generator/meta.yaml`
- Create: `apps/api/src/main/resources/skills/cardflow.html-card-generator/SKILL.md`

**Interfaces:**
- Consumes: 7 hardcoded sections from `apps/api/src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java` lines 171-306
- Produces: versionable Markdown file on classpath

- [ ] **Step 2.1: Create meta.yaml**

Create file `apps/api/src/main/resources/skills/cardflow.html-card-generator/meta.yaml`:

```yaml
name: cardflow.html-card-generator
description: 生成符合 CardFlow html_card 协议的动态信息卡(小红书/抖音/B站/YouTube)
whenToApply: 用户调用内容生成接口、generationMode=topic 时
tags:
  - topic
  - html_card
version: 1
```

- [ ] **Step 2.2: Extract 7 sections to SKILL.md**

Create file `apps/api/src/main/resources/skills/cardflow.html-card-generator/SKILL.md` with the 7 sections concatenated. Copy verbatim from `DeepSeekLlmProvider.java` (each `*Section()` method body), preserving the Chinese Markdown headings. Final file must start with `# CardFlow html_card 生成协议` and contain all seven sections: 角色, 输出协议, 内容分析, 布局选择, 视觉风格, HTML 约束, 质量约束.

Use the full text from these methods (lines 188-306 of `DeepSeekLlmProvider.java`), joining them with `---` separators and unifying the heading style (`##` for top sections, `###` for subsections). Example structure:

```markdown
# CardFlow html_card 生成协议

## 角色
...

## 输出协议
...

## 内容分析
...

## 布局选择
...

## 视觉风格
...

## HTML 约束
...

## 质量约束
...
```

- [ ] **Step 2.3: Verify file loads as classpath resource**

Temporarily add this to any existing test (e.g. `GenerationServiceTest.setUp()`):

```java
Resource skill = new ClassPathResource("skills/cardflow.html-card-generator/SKILL.md");
assertThat(skill.exists()).isTrue();
```

Run: `cd apps/api && mvn -q test -Dtest=GenerationServiceTest`
Expected: PASS. Then remove the temporary assertion.

- [ ] **Step 2.4: Commit**

```bash
git add apps/api/src/main/resources/skills/
git commit -m "feat(skill): add cardflow.html-card-generator SKILL.md and meta.yaml"
```

---

## Task 3: Add SkillMeta record

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/skill/SkillMeta.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/skill/SkillMetaTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `SkillMeta` record consumed by Task 4 (`FileSystemSkillRegistry`)

- [ ] **Step 3.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/skill/SkillMetaTest.java`:

```java
package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SkillMetaTest {
  @Test
  void recordExposesAllFields() {
    ByteArrayResource resource = new ByteArrayResource("body".getBytes());
    SkillMeta meta = new SkillMeta(
      "cardflow.html-card-generator",
      "生成 CardFlow 信息卡",
      "topic 模式生成时",
      List.of("topic", "html_card"),
      "1",
      resource
    );

    assertThat(meta.name()).isEqualTo("cardflow.html-card-generator");
    assertThat(meta.description()).isEqualTo("生成 CardFlow 信息卡");
    assertThat(meta.whenToApply()).isEqualTo("topic 模式生成时");
    assertThat(meta.tags()).containsExactly("topic", "html_card");
    assertThat(meta.version()).isEqualTo("1");
    assertThat(meta.contentResource()).isSameAs(resource);
  }
}
```

- [ ] **Step 3.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=SkillMetaTest`
Expected: FAIL with "SkillMeta cannot be resolved to a type".

- [ ] **Step 3.3: Create SkillMeta record**

Create `apps/api/src/main/java/ai/cardflow/api/skill/SkillMeta.java`:

```java
package ai.cardflow.api.skill;

import java.util.List;
import org.springframework.core.io.Resource;

/**
 * 单个 Skill 的元数据。
 *
 * <p>对应 classpath:skills/&lt;name&gt;/meta.yaml 加同一目录下的 SKILL.md。</p>
 */
public record SkillMeta(
  String name,
  String description,
  String whenToApply,
  List<String> tags,
  String version,
  Resource contentResource
) {}
```

- [ ] **Step 3.4: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=SkillMetaTest`
Expected: PASS.

- [ ] **Step 3.5: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/skill/SkillMeta.java apps/api/src/test/java/ai/cardflow/api/skill/SkillMetaTest.java
git commit -m "feat(skill): add SkillMeta record"
```

---

## Task 4: Add SkillSummaryFormatter

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/skill/SkillSummaryFormatter.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/skill/SkillSummaryFormatterTest.java`

**Interfaces:**
- Consumes: `List<SkillMeta>`
- Produces: bullet-list string `"<skill name>: <description>"` per line

- [ ] **Step 4.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/skill/SkillSummaryFormatterTest.java`:

```java
package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class SkillSummaryFormatterTest {
  private final SkillSummaryFormatter formatter = new SkillSummaryFormatter();

  @Test
  void formatsEachSkillAsBulletLine() {
    SkillMeta a = new SkillMeta("cardflow.html-card-generator", "生成 CardFlow 信息卡", "topic 模式", List.of(), "1", new ByteArrayResource(new byte[0]));
    SkillMeta b = new SkillMeta("cardflow.article-generator", "拆解长文为卡片大纲", "article 模式", List.of(), "1", new ByteArrayResource(new byte[0]));

    String summary = formatter.format(List.of(a, b));

    assertThat(summary).contains("- cardflow.html-card-generator: 生成 CardFlow 信息卡");
    assertThat(summary).contains("- cardflow.article-generator: 拆解长文为卡片大纲");
  }

  @Test
  void returnsEmptyStringForEmptyList() {
    assertThat(formatter.format(List.of())).isEmpty();
  }

  @Test
  void sortsAlphabeticallyByName() {
    SkillMeta b = new SkillMeta("cardflow.b-generator", "B", "", List.of(), "1", new ByteArrayResource(new byte[0]));
    SkillMeta a = new SkillMeta("cardflow.a-generator", "A", "", List.of(), "1", new ByteArrayResource(new byte[0]));

    String summary = formatter.format(List.of(b, a));

    assertThat(summary.indexOf("cardflow.a-generator")).isLessThan(summary.indexOf("cardflow.b-generator"));
  }
}
```

- [ ] **Step 4.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=SkillSummaryFormatterTest`
Expected: FAIL with "SkillSummaryFormatter cannot be resolved".

- [ ] **Step 4.3: Create SkillSummaryFormatter**

Create `apps/api/src/main/java/ai/cardflow/api/skill/SkillSummaryFormatter.java`:

```java
package ai.cardflow.api.skill;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 把 {@link SkillMeta} 列表格式化为 LLM 可读的 Skill 摘要。
 *
 * <p>输出每行一个 Skill,形如 {@code - <name>: <description>}。
 * 列表按 name 字典序排序,保证多次注入顺序稳定。</p>
 */
@Component
public class SkillSummaryFormatter {

  /**
   * 把 Skill 列表格式化为多行 bullet 文本。
   *
   * @param skills Skill 元数据列表
   * @return 多行 bullet 文本(无尾部换行)
   */
  public String format(List<SkillMeta> skills) {
    return skills.stream()
      .sorted(Comparator.comparing(SkillMeta::name))
      .map(meta -> "- " + meta.name() + ": " + meta.description())
      .collect(Collectors.joining("\n"));
  }
}
```

- [ ] **Step 4.4: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=SkillSummaryFormatterTest`
Expected: PASS.

- [ ] **Step 4.5: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/skill/SkillSummaryFormatter.java apps/api/src/test/java/ai/cardflow/api/skill/SkillSummaryFormatterTest.java
git commit -m "feat(skill): add SkillSummaryFormatter"
```

---

## Task 5: Add SkillRegistry interface and FileSystemSkillRegistry

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/skill/SkillRegistry.java`
- Create: `apps/api/src/main/java/ai/cardflow/api/skill/FileSystemSkillRegistry.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/skill/FileSystemSkillRegistryTest.java`

**Interfaces:**
- Consumes: `ResourceLoader` (Spring), `ObjectMapper` (Jackson YAML), `SkillSummaryFormatter`
- Produces: `SkillRegistry` bean; `all()`, `find(name)`, `summary()`, `readFullContent(name)` methods

- [ ] **Step 5.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/skill/FileSystemSkillRegistryTest.java`:

```java
package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

class FileSystemSkillRegistryTest {
  @TempDir
  Path tempDir;

  private FileSystemSkillRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectory(tempDir.resolve("skills"));
    Path skillDir = Files.createDirectory(tempDir.resolve("skills/cardflow.html-card-generator"));
    Files.writeString(skillDir.resolve("meta.yaml"), """
        name: cardflow.html-card-generator
        description: 生成 CardFlow 信息卡
        whenToApply: topic 模式
        tags:
          - topic
        version: 1
        """);
    Files.writeString(skillDir.resolve("SKILL.md"), "# body\n");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    registry = new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", tempDir.toString());
  }

  @Test
  void loadsAllMetadata() {
    assertThat(registry.all()).hasSize(1);
    SkillMeta meta = registry.all().get(0);
    assertThat(meta.name()).isEqualTo("cardflow.html-card-generator");
    assertThat(meta.description()).isEqualTo("生成 CardFlow 信息卡");
    assertThat(meta.whenToApply()).isEqualTo("topic 模式");
    assertThat(meta.tags()).containsExactly("topic");
    assertThat(meta.version()).isEqualTo("1");
  }

  @Test
  void findsByName() {
    assertThat(registry.find("cardflow.html-card-generator")).isPresent();
    assertThat(registry.find("unknown")).isEmpty();
  }

  @Test
  void readsFullSkillContent() {
    String content = registry.readFullContent("cardflow.html-card-generator");
    assertThat(content).isEqualTo("# body\n");
  }

  @Test
  void readFullContentThrowsOnUnknown() {
    assertThatThrownBy(() -> registry.readFullContent("nope"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Unknown skill: nope");
  }

  @Test
  void summaryIsNonEmpty() {
    assertThat(registry.summary()).contains("cardflow.html-card-generator");
  }

  @Test
  void failsWhenNameFormatIsInvalid(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path bad = Files.createDirectory(otherTemp.resolve("skills/BadName"));
    Files.writeString(bad.resolve("meta.yaml"), """
        name: BadName
        description: x
        whenToApply: y
        version: 1
        """);
    Files.writeString(bad.resolve("SKILL.md"), "body");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Invalid skill name");
  }

  @Test
  void failsWhenDirectoryNameDoesNotMatch(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path skill = Files.createDirectory(otherTemp.resolve("skills/wrong-dir"));
    Files.writeString(skill.resolve("meta.yaml"), """
        name: cardflow.html-card-generator
        description: x
        whenToApply: y
        version: 1
        """);
    Files.writeString(skill.resolve("SKILL.md"), "body");

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("does not match directory");
  }

  @Test
  void failsWhenSkillMdMissing(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    Path skill = Files.createDirectory(otherTemp.resolve("skills/cardflow.test"));
    Files.writeString(skill.resolve("meta.yaml"), """
        name: cardflow.test
        description: x
        whenToApply: y
        version: 1
        """);

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Missing SKILL.md");
  }

  @Test
  void failsWhenNameDuplicate(@TempDir Path otherTemp) throws IOException {
    Files.createDirectory(otherTemp.resolve("skills"));
    for (String dir : new String[]{"cardflow.dup", "cardflow.dup-other"}) {
      Path skill = Files.createDirectory(otherTemp.resolve("skills/" + dir));
      Files.writeString(skill.resolve("meta.yaml"), """
          name: cardflow.dup
          description: x
          whenToApply: y
          version: 1
          """);
      Files.writeString(skill.resolve("SKILL.md"), "body");
    }

    ResourceLoader loader = new DefaultResourceLoader();
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    assertThatThrownBy(() -> new FileSystemSkillRegistry(loader, yamlMapper, new SkillSummaryFormatter(), "skills/**/meta.yaml", otherTemp.toString()))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Duplicate skill name");
  }
}
```

- [ ] **Step 5.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=FileSystemSkillRegistryTest`
Expected: FAIL with "FileSystemSkillRegistry cannot be resolved".

- [ ] **Step 5.3: Create SkillRegistry interface**

Create `apps/api/src/main/java/ai/cardflow/api/skill/SkillRegistry.java`:

```java
package ai.cardflow.api.skill;

import java.util.List;
import java.util.Optional;

/**
 * Skill 注册表。
 *
 * <p>集中提供 Skill 元数据查询、摘要文本生成和完整内容读取。
 * 启动时从 classpath 扫描所有 Skill,加载失败应 fail-fast。</p>
 */
public interface SkillRegistry {
  /**
   * 返回所有已加载的 Skill 元数据。
   *
   * @return 不可变列表
   */
  List<SkillMeta> all();

  /**
   * 按名称查找 Skill。
   *
   * @param name Skill 名称
   * @return 找到的 Skill 元数据,否则 {@code Optional.empty()}
   */
  Optional<SkillMeta> find(String name);

  /**
   * 返回所有 Skill 的摘要文本,用于拼到 system prompt 末尾。
   *
   * @return 多行 bullet 列表
   */
  String summary();

  /**
   * 读取某个 Skill 的 SKILL.md 完整内容。
   *
   * @param name Skill 名称
   * @return 完整 Markdown 内容
   * @throws IllegalArgumentException Skill 不存在时
   */
  String readFullContent(String name);
}
```

- [ ] **Step 5.4: Create FileSystemSkillRegistry**

Create `apps/api/src/main/java/ai/cardflow/api/skill/FileSystemSkillRegistry.java`:

```java
package ai.cardflow.api.skill;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 从 classpath 中扫描 Skill 目录并构建 SkillRegistry。
 *
 * <p>约定:classpath:{@code basePattern} 下所有 meta.yaml 描述一个 Skill。
 * 加载时执行 5 项校验(见 README 失败模式),失败抛 {@link IllegalStateException}。</p>
 */
@Component
public class FileSystemSkillRegistry implements SkillRegistry {
  private static final Pattern NAME_PATTERN = Pattern.compile("^[a-z0-9]+(\\.[a-z0-9-]+)+$");

  private final Map<String, SkillMeta> skills;
  private final SkillSummaryFormatter formatter;

  /**
   * Spring 容器使用的构造函数。
   */
  public FileSystemSkillRegistry(
    ResourceLoader loader,
    ObjectMapper yamlMapper,
    SkillSummaryFormatter formatter
  ) {
    this(loader, yamlMapper, formatter, "classpath:skills/**/meta.yaml", null);
  }

  /**
   * 测试用构造函数,允许传入文件系统根目录和 glob 模式。
   */
  public FileSystemSkillRegistry(
    ResourceLoader loader,
    ObjectMapper yamlMapper,
    SkillSummaryFormatter formatter,
    String classpathPattern,
    String fileSystemRoot
  ) {
    this.formatter = formatter;
    this.skills = loadFromClasspath(loader, yamlMapper, classpathPattern, fileSystemRoot);
  }

  @Override
  public List<SkillMeta> all() {
    return List.copyOf(skills.values());
  }

  @Override
  public Optional<SkillMeta> find(String name) {
    return Optional.ofNullable(skills.get(name));
  }

  @Override
  public String summary() {
    return formatter.format(all());
  }

  @Override
  public String readFullContent(String name) {
    SkillMeta meta = skills.get(name);
    if (meta == null) {
      throw new IllegalArgumentException("Unknown skill: " + name);
    }
    try {
      return StreamUtils.copyToString(meta.contentResource().getInputStream(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read SKILL.md for " + name, e);
    }
  }

  private Map<String, SkillMeta> loadFromClasspath(
    ResourceLoader loader,
    ObjectMapper yamlMapper,
    String pattern,
    String fileSystemRoot
  ) {
    Map<String, SkillMeta> map = new LinkedHashMap<>();
    List<Path> metaFiles = discoverMetaFiles(fileSystemRoot, pattern);
    for (Path metaFile : metaFiles) {
      SkillMeta meta = parseMeta(yamlMapper, metaFile);
      validateMeta(meta, metaFile);
      map.put(meta.name(), meta);
    }
    return map;
  }

  private List<Path> discoverMetaFiles(String fileSystemRoot, String pattern) {
    if (fileSystemRoot != null) {
      try {
        List<Path> result = new ArrayList<>();
        Path root = Path.of(fileSystemRoot);
        if (!Files.exists(root)) {
          throw new IllegalStateException("Skill root not found: " + fileSystemRoot);
        }
        try (var stream = Files.walk(root)) {
          stream.filter(p -> p.getFileName().toString().equals("meta.yaml")).forEach(result::add);
        }
        return result;
      } catch (IOException e) {
        throw new IllegalStateException("Failed to scan skills directory: " + fileSystemRoot, e);
      }
    }
    // Production path: use classpath glob. Simplified implementation: load classpath:skills/ directly.
    Resource dir = loader.getResource("classpath:skills/");
    List<Path> result = new ArrayList<>();
    try {
      Path skillsDir = Path.of(dir.getURI());
      try (var stream = Files.walk(skillsDir)) {
        stream.filter(p -> p.getFileName().toString().equals("meta.yaml")).forEach(result::add);
      }
      return result;
    } catch (IOException | java.net.URISyntaxException e) {
      throw new IllegalStateException("Failed to scan classpath skills", e);
    }
  }

  private SkillMeta parseMeta(ObjectMapper yamlMapper, Path metaFile) {
    try {
      Map<String, Object> raw = yamlMapper.readValue(metaFile.toFile(), Map.class);
      String name = (String) raw.get("name");
      String description = (String) raw.get("description");
      String whenToApply = (String) raw.get("whenToApply");
      @SuppressWarnings("unchecked")
      List<String> tags = (List<String>) raw.getOrDefault("tags", List.of());
      String version = (String) raw.getOrDefault("version", "1");
      if (name == null || description == null || whenToApply == null) {
        throw new IllegalStateException("Skill meta.yaml missing required field: " + metaFile);
      }
      Path skillDir = metaFile.getParent();
      Path skillMd = skillDir.resolve("SKILL.md");
      if (!Files.exists(skillMd) || Files.size(skillMd) == 0) {
        throw new IllegalStateException("Missing or empty SKILL.md at " + skillMd);
      }
      Resource contentResource = new org.springframework.core.io.UrlResource(skillMd.toUri());
      return new SkillMeta(name, description, whenToApply, tags, version, contentResource);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse meta.yaml: " + metaFile, e);
    }
  }

  private void validateMeta(SkillMeta meta, Path metaFile) {
    Path skillDir = metaFile.getParent();
    String dirName = skillDir.getFileName().toString();
    if (!dirName.equals(meta.name())) {
      throw new IllegalStateException(
        "Skill directory name " + dirName + " does not match directory name in meta.yaml: " + metaFile
      );
    }
    if (!NAME_PATTERN.matcher(meta.name()).matches()) {
      throw new IllegalStateException("Invalid skill name (must match " + NAME_PATTERN.pattern() + "): " + meta.name());
    }
    if (skills.containsKey(meta.name())) {
      throw new IllegalStateException("Duplicate skill name: " + meta.name());
    }
  }
}
```

**Note:** The `validateMeta` method assumes `skills` is being populated; the implementation mutates the map during iteration. The duplicate check works because `loadFromClasspath` calls `map.put` after validation but a fresh `LinkedHashMap` is built first — to keep validation order, refactor `loadFromClasspath` to check duplicates inside the loop. Adjust as follows in the implementation above:

Replace the final `map.put(meta.name(), meta);` line inside `loadFromClasspath` with:

```java
  if (map.containsKey(meta.name())) {
    throw new IllegalStateException("Duplicate skill name: " + meta.name());
  }
  map.put(meta.name(), meta);
```

And delete the `if (skills.containsKey(...))` check from `validateMeta`.

- [ ] **Step 5.5: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=FileSystemSkillRegistryTest`
Expected: PASS.

- [ ] **Step 5.6: Run full test suite to verify nothing else broke**

Run: `cd apps/api && mvn -q test`
Expected: PASS. Existing tests still work because `DeepSeekLlmProvider` is still wired and `FileSystemSkillRegistry` is a new bean Spring instantiates at startup (it will load the real `classpath:skills/cardflow.html-card-generator/`).

- [ ] **Step 5.7: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/skill/ apps/api/src/test/java/ai/cardflow/api/skill/
git commit -m "feat(skill): add SkillRegistry and FileSystemSkillRegistry"
```

---

## Task 6: Add SkillContentSanityTest

**Files:**
- Create: `apps/api/src/test/java/ai/cardflow/api/skill/SkillContentSanityTest.java`

**Interfaces:**
- Consumes: `classpath:skills/cardflow.html-card-generator/SKILL.md`
- Produces: regression test ensuring 7 sections are not silently lost during future edits

- [ ] **Step 6.1: Write the test**

Create `apps/api/src/test/java/ai/cardflow/api/skill/SkillContentSanityTest.java`:

```java
package ai.cardflow.api.skill;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

/**
 * 卡死 SKILL.md 中必须存在的关键内容,防止未来编辑时误删。
 */
class SkillContentSanityTest {

  @Test
  void htmlCardGeneratorSkillContainsAllSevenSections() throws Exception {
    String content = StreamUtils.copyToString(
      new ClassPathResource("skills/cardflow.html-card-generator/SKILL.md").getInputStream(),
      StandardCharsets.UTF_8
    );

    assertThat(content).contains("html_card");
    assertThat(content).contains("designNotes");
    assertThat(content).contains("布局选择");
    assertThat(content).contains("comparison");
    assertThat(content).contains("flow");
    assertThat(content).contains("mindmap");
    assertThat(content).contains("quadrant");
    assertThat(content).contains("不允许出现大面积空白");
    assertThat(content).contains("禁止 script").doesNotContain("placeholder");
  }

  @Test
  void skillSizeStaysUnderLimit() throws Exception {
    String content = StreamUtils.copyToString(
      new ClassPathResource("skills/cardflow.html-card-generator/SKILL.md").getInputStream(),
      StandardCharsets.UTF_8
    );

    assertThat(content.getBytes(StandardCharsets.UTF_8).length)
      .as("SKILL.md must stay under 4KB to avoid blowing context window")
      .isLessThan(4096);
  }
}
```

- [ ] **Step 6.2: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=SkillContentSanityTest`
Expected: PASS. If the size check fails because real SKILL.md exceeds 4KB, loosen to a soft warning OR trim prose in SKILL.md while preserving meaning. Document the chosen limit in a comment.

- [ ] **Step 6.3: Commit**

```bash
git add apps/api/src/test/java/ai/cardflow/api/skill/SkillContentSanityTest.java
git commit -m "test(skill): add SkillContentSanityTest for 7 sections and size"
```

---

## Task 7: Add SkillRoutingAdvisor

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/llm/SkillRoutingAdvisor.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/llm/SkillRoutingAdvisorTest.java`

**Interfaces:**
- Consumes: `SkillRegistry`
- Produces: `BaseAdvisor` that prepends Skill summary to `request.systemText` in `before()`

- [ ] **Step 7.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/llm/SkillRoutingAdvisorTest.java`:

```java
package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.cardflow.api.skill.SkillRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

class SkillRoutingAdvisorTest {

  @Test
  void beforeAppendsSkillSummaryToSystemText() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.summary()).thenReturn("- cardflow.html-card-generator: 生成 CardFlow 信息卡");

    AdvisorChain chain = mock(AdvisorChain.class);
    Prompt prompt = new Prompt(List.of(new SystemMessage("ORIGINAL")));
    ChatClientRequest request = ChatClientRequest.builder().prompt(prompt).build();

    SkillRoutingAdvisor advisor = new SkillRoutingAdvisor(registry);
    ChatClientRequest updated = advisor.before(request, chain);

    assertThat(updated.prompt().getSystemMessage().getText())
      .startsWith("ORIGINAL")
      .contains("可用 Skills")
      .contains("read_skill")
      .contains("- cardflow.html-card-generator: 生成 CardFlow 信息卡");
  }

  @Test
  void afterReturnsResponseUnchanged() {
    SkillRegistry registry = mock(SkillRegistry.class);
    SkillRoutingAdvisor advisor = new SkillRoutingAdvisor(registry);

    assertThat(advisor.after(null, mock(AdvisorChain.class))).isNull();
  }

  @Test
  void getOrderIsZero() {
    assertThat(new SkillRoutingAdvisor(mock(SkillRegistry.class)).getOrder()).isEqualTo(0);
  }
}
```

- [ ] **Step 7.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=SkillRoutingAdvisorTest`
Expected: FAIL with "SkillRoutingAdvisor cannot be resolved".

- [ ] **Step 7.3: Create SkillRoutingAdvisor**

Create `apps/api/src/main/java/ai/cardflow/api/llm/SkillRoutingAdvisor.java`:

```java
package ai.cardflow.api.llm;

import ai.cardflow.api.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

/**
 * 把当前可用 Skill 摘要拼到 system prompt 末尾。
 *
 * <p>每次请求都会执行,LLM 在 system 提示中看到 Skill 列表,
 * 详细规则仍需通过 {@code read_skill} 工具按需读取。</p>
 */
@Component
public class SkillRoutingAdvisor implements BaseAdvisor {
  private static final String SKILLS_HEADER = """

      ## 可用 Skills

      以下 Skills 可用。需要详细规则时,调用 read_skill 工具读取完整 SKILL.md。

      %s

      """;

  private final SkillRegistry registry;

  public SkillRoutingAdvisor(SkillRegistry registry) {
    this.registry = registry;
  }

  @Override
  public int getOrder() {
    return 0;
  }

  @Override
  public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    String summary = registry.summary();
    Prompt prompt = request.prompt();
    String currentSystem = prompt.getSystemMessage().getText();
    String augmented = currentSystem + SKILLS_HEADER.formatted(summary);
    return request.mutate()
      .prompt(prompt.augmentSystemMessage(augmented))
      .build();
  }

  @Override
  public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
    return response;
  }
}
```

- [ ] **Step 7.4: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=SkillRoutingAdvisorTest`
Expected: PASS. If `ChatClientRequest.builder()` API differs in your Spring AI version, adjust to the actual API surface; the test verifies behavior, the implementation may need minor API tweaks (e.g. `ChatClientRequest.create(prompt)` vs builder).

- [ ] **Step 7.5: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/llm/SkillRoutingAdvisor.java apps/api/src/test/java/ai/cardflow/api/llm/SkillRoutingAdvisorTest.java
git commit -m "feat(llm): add SkillRoutingAdvisor appending Skill summary"
```

---

## Task 8: Add ReadSkillTool

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/llm/ReadSkillTool.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/llm/ReadSkillToolTest.java`

**Interfaces:**
- Consumes: `SkillRegistry`
- Produces: `ToolCallback` named `read_skill`; on success returns full SKILL.md, on error returns `{"error":"..."}` JSON

- [ ] **Step 8.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/llm/ReadSkillToolTest.java`:

```java
package ai.cardflow.api.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.cardflow.api.skill.SkillRegistry;
import org.junit.jupiter.api.Test;

class ReadSkillToolTest {

  @Test
  void returnsFullContentForKnownSkill() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.readFullContent("cardflow.html-card-generator")).thenReturn("# body");

    String result = new ReadSkillTool(registry).call("{\"name\":\"cardflow.html-card-generator\"}");

    assertThat(result).isEqualTo("# body");
  }

  @Test
  void returnsErrorJsonForUnknownSkill() {
    SkillRegistry registry = mock(SkillRegistry.class);
    when(registry.readFullContent("nope"))
      .thenThrow(new IllegalArgumentException("Unknown skill: nope"));

    String result = new ReadSkillTool(registry).call("{\"name\":\"nope\"}");

    assertThat(result).contains("\"error\"");
    assertThat(result).contains("Unknown skill");
  }

  @Test
  void returnsErrorJsonForMalformedArguments() {
    SkillRegistry registry = mock(SkillRegistry.class);
    String result = new ReadSkillTool(registry).call("not json");

    assertThat(result).contains("\"error\"");
    assertThat(result).contains("Invalid arguments");
  }

  @Test
  void returnsErrorJsonWhenNameFieldMissing() {
    SkillRegistry registry = mock(SkillRegistry.class);
    String result = new ReadSkillTool(registry).call("{\"other\":\"value\"}");

    assertThat(result).contains("\"error\"");
  }

  @Test
  void exposesNameAndSchema() {
    ReadSkillTool tool = new ReadSkillTool(mock(SkillRegistry.class));
    assertThat(tool.getName()).isEqualTo("read_skill");
    assertThat(tool.getInputSchema()).contains("\"name\"");
  }
}
```

- [ ] **Step 8.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=ReadSkillToolTest`
Expected: FAIL with "ReadSkillTool cannot be resolved".

- [ ] **Step 8.3: Create ReadSkillTool**

Create `apps/api/src/main/java/ai/cardflow/api/llm/ReadSkillTool.java`:

```java
package ai.cardflow.api.llm;

import ai.cardflow.api.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

/**
 * LLM 可调用的 read_skill 工具,按需读取某个 Skill 的完整 SKILL.md。
 *
 * <p>失败(未知 Skill、参数错误)返回 error JSON 而不抛异常,
 * 让 LLM 在下一轮看到错误并自行恢复。</p>
 */
@Component
public class ReadSkillTool implements ToolCallback {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SCHEMA = """
      {
        "type": "object",
        "properties": {
          "name": {
            "type": "string",
            "description": "Skill 名称,例如 cardflow.html-card-generator"
          }
        },
        "required": ["name"]
      }
      """;

  private final SkillRegistry registry;

  public ReadSkillTool(SkillRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String getName() {
    return "read_skill";
  }

  @Override
  public String getDescription() {
    return "按需读取某个 Skill 的完整 SKILL.md 内容,获取详细的协议/约束/风格规则";
  }

  @Override
  public String getInputSchema() {
    return SCHEMA;
  }

  @Override
  public String call(String jsonArguments) {
    try {
      JsonNode args = MAPPER.readTree(jsonArguments);
      JsonNode nameNode = args.get("name");
      if (nameNode == null || !nameNode.isTextual()) {
        return "{\"error\":\"Invalid arguments: missing 'name' field\"}";
      }
      return registry.readFullContent(nameNode.asText());
    } catch (Exception e) {
      return "{\"error\":\"Unknown skill: " + e.getMessage() + "\"}";
    }
  }
}
```

- [ ] **Step 8.4: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=ReadSkillToolTest`
Expected: PASS.

- [ ] **Step 8.5: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/llm/ReadSkillTool.java apps/api/src/test/java/ai/cardflow/api/llm/ReadSkillToolTest.java
git commit -m "feat(llm): add ReadSkillTool exposing SKILL.md content"
```

---

## Task 9: Add HtmlCardValidator

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/service/HtmlCardValidator.java`
- Create: `apps/api/src/test/java/ai/cardflow/api/service/HtmlCardValidatorTest.java`

**Interfaces:**
- Consumes: `JsonNode`
- Produces: throws `IllegalStateException` on protocol violation; no return value on success

- [ ] **Step 9.1: Write the failing test**

Create `apps/api/src/test/java/ai/cardflow/api/service/HtmlCardValidatorTest.java`:

```java
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
  void acceptsValidCard() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><head></head><body></body></html>","designNotes":"","warnings":[]}
        """);
    assertThatCode(() -> validator.validate(node)).doesNotThrowAnyException();
  }

  @Test
  void rejectsMissingKind() {
    JsonNode node = mapper.readTree("""
        {"title":"x","html":"<!doctype html><html></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("html_card");
  }

  @Test
  void rejectsBlankTitle() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"  ","html":"<!doctype html><html></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("title");
  }

  @Test
  void rejectsBlankHtml() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":""}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("html");
  }

  @Test
  void rejectsHtmlWithoutHtmlTags() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<body>just body</body>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("完整 HTML 文档");
  }

  @Test
  void rejectsScriptTag() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><body><script>alert(1)</script></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("script");
  }

  @Test
  void rejectsExternalUrl() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><body><a href='https://example.com'>x</a></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("外部资源");
  }

  @Test
  void rejectsImportDirective() {
    JsonNode node = mapper.readTree("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><head><style>@import url('foo.css')</style></head><body></body></html>"}
        """);
    assertThatThrownBy(() -> validator.validate(node)).isInstanceOf(IllegalStateException.class).hasMessageContaining("外部样式资源");
  }
}
```

- [ ] **Step 9.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=HtmlCardValidatorTest`
Expected: FAIL with "HtmlCardValidator cannot be resolved".

- [ ] **Step 9.3: Create HtmlCardValidator**

Create `apps/api/src/main/java/ai/cardflow/api/service/HtmlCardValidator.java`:

```java
package ai.cardflow.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * 校验 DeepSeek 返回的 html_card JSON 是否符合 CardFlow 协议。
 *
 * <p>从 {@code DeepSeekLlmProvider} 提取而来,逻辑不变。</p>
 */
@Component
public class HtmlCardValidator {

  /**
   * 校验模型返回的 JSON 是否符合 html_card 协议和基础安全边界。
   *
   * @param root 模型返回 JSON
   * @throws IllegalStateException 协议被违反时
   */
  public void validate(JsonNode root) {
    if (!"html_card".equals(root.path("kind").asText())) {
      throw new IllegalStateException("DeepSeek 返回的 kind 必须是 html_card。");
    }
    if (root.path("title").asText("").isBlank()) {
      throw new IllegalStateException("DeepSeek 返回的 title 不能为空。");
    }
    String html = root.path("html").asText("");
    if (html.isBlank()) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能为空。");
    }
    if (!html.toLowerCase().contains("<html") || !html.toLowerCase().contains("</html>")) {
      throw new IllegalStateException("DeepSeek 返回的 html 必须是完整 HTML 文档。");
    }
    assertSafeHtml(html);
  }

  private void assertSafeHtml(String html) {
    String lower = html.toLowerCase();
    if (lower.contains("<script") || lower.contains("</script")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含 script。");
    }
    if (lower.contains("http://") || lower.contains("https://") || lower.contains("src=") || lower.contains("href=")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含外部资源。");
    }
    if (lower.contains("@import") || lower.contains("url(")) {
      throw new IllegalStateException("DeepSeek 返回的 html 不能包含外部样式资源。");
    }
  }
}
```

- [ ] **Step 9.4: Run test to verify it passes**

Run: `cd apps/api && mvn -q test -Dtest=HtmlCardValidatorTest`
Expected: PASS.

- [ ] **Step 9.5: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/service/HtmlCardValidator.java apps/api/src/test/java/ai/cardflow/api/service/HtmlCardValidatorTest.java
git commit -m "feat(service): extract HtmlCardValidator from DeepSeekLlmProvider"
```

---

## Task 10: Wire ChatClient and refactor GenerationService

**Files:**
- Create: `apps/api/src/main/java/ai/cardflow/api/llm/ChatClientConfig.java`
- Modify: `apps/api/src/main/java/ai/cardflow/api/service/GenerationService.java`
- Modify: `apps/api/src/main/java/ai/cardflow/api/CardflowApiApplication.java` (remove autoconfig exclusion)
- Modify: `apps/api/src/test/java/ai/cardflow/api/GenerationServiceTest.java`

**Interfaces:**
- Consumes: `OpenAiApi`, `SkillRegistry`, `ReadSkillTool`, `HtmlCardValidator`
- Produces: Spring `@Bean ChatClient`; `GenerationService.generate()` returns equivalent JSON

- [ ] **Step 10.1: Write the failing test**

Update `apps/api/src/test/java/ai/cardflow/api/GenerationServiceTest.java`. The existing test uses a custom `TestLlmProvider`; refactor it to use a mocked `ChatClient`. Replace the file content with:

```java
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
  private ChatClient chatClient;

  @BeforeEach
  void setUp() {
    AppProperties properties = new AppProperties(
      new AppProperties.Storage("storage/outputs"),
      new AppProperties.App("local-user"),
      new AppProperties.Llm("deepseek", "https://api.deepseek.com", "deepseek-chat", "test-key", 5000)
    );
    DriverManagerDataSource dataSource = new DriverManagerDataSource("jdbc:sqlite:" + tempDir.resolve("cardflow-test.db"));
    dataSource.setDriverClassName("org.sqlite.JDBC");
    jdbc = new JdbcTemplate(dataSource);
    new DatabaseInitializer(jdbc, properties).run(null);

    chatClient = mock(ChatClient.class);
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
    when(chatClient.prompt(any(ChatClientRequestSpecSetup.class))).thenReturn(requestSpec);
    when(requestSpec.system(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(responseSpec);
    when(responseSpec.content()).thenReturn("""
        {"kind":"html_card","title":"x","html":"<!doctype html><html><head></head><body></body></html>","designNotes":"test","warnings":[]}
        """);

    generationService = new GenerationService(jdbc, properties, chatClient, new HtmlCardValidator(), new ObjectMapper());
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
    Integer usageCount = jdbc.queryForObject("select count(*) from usage_record where model_name = 'deepseek-chat'", Integer.class);
    assertThat(taskCount).isEqualTo(1);
    assertThat(usageCount).isEqualTo(1);
  }
}
```

**Note:** Spring AI 1.0.x `ChatClient` uses a fluent spec builder; the exact mocking approach depends on the API. If `chatClient.prompt()` returns a builder-like object and `.system()` / `.user()` chain, mock accordingly. The implementation in Step 10.3 will use the actual API; mock what that code calls.

- [ ] **Step 10.2: Run test to verify it fails**

Run: `cd apps/api && mvn -q test -Dtest=GenerationServiceTest`
Expected: FAIL because `GenerationService` constructor doesn't yet accept `ChatClient`.

- [ ] **Step 10.3: Refactor GenerationService**

Replace `apps/api/src/main/java/ai/cardflow/api/service/GenerationService.java` with:

```java
package ai.cardflow.api.service;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.model.ApiModels.GenerateContentRequest;
import ai.cardflow.api.model.ApiModels.GenerateContentResponse;
import ai.cardflow.api.model.ApiModels.TopicInput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 内容生成服务。
 *
 * <p>负责记录生成任务、调用 Spring AI {@link ChatClient}、
 * 写入用量记录,并把结构化 JSON 返回给前端。</p>
 */
@Service
public class GenerationService {
  private static final String MODEL_NAME = "deepseek-chat";

  private final JdbcTemplate jdbc;
  private final AppProperties properties;
  private final ChatClient chatClient;
  private final HtmlCardValidator validator;
  private final ObjectMapper objectMapper;

  public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ChatClient chatClient,
    HtmlCardValidator validator,
    ObjectMapper objectMapper
  ) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.chatClient = chatClient;
    this.validator = validator;
    this.objectMapper = objectMapper;
  }

  public GenerateContentResponse generate(GenerateContentRequest request) {
    String taskId = UUID.randomUUID().toString();
    String now = Instant.now().toString();
    String inputText = inputText(request);
    String paramsJson = toJson(Map.of(
      "generationMode", request.generationMode(),
      "outputFormat", request.outputFormat(),
      "renderMode", request.renderMode(),
      "templateId", request.templateId()
    ));

    jdbc.update("""
        insert into generate_task
        (id, user_id, project_id, task_type, input_text, input_params_json, status, error_message, started_at, finished_at, created_at)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        taskId,
        properties.app().defaultUserId(),
        null,
        "content_generation",
        inputText,
        paramsJson,
        "running",
        null,
        now,
        null,
        now
    );

    try {
      String contentJson = chatClient.prompt()
        .system(buildSystemPrompt())
        .user(buildUserPrompt(request))
        .call()
        .content();
      validator.validate(objectMapper.readTree(contentJson));
      String finishedAt = Instant.now().toString();
      jdbc.update("update generate_task set status = ?, finished_at = ? where id = ?", "succeeded", finishedAt, taskId);
      jdbc.update("""
          insert into usage_record (id, user_id, task_id, usage_type, amount, model_name, created_at)
          values (?, ?, ?, ?, ?, ?, ?)
          """,
          UUID.randomUUID().toString(),
          properties.app().defaultUserId(),
          taskId,
          "content_generation",
          1,
          MODEL_NAME,
          finishedAt
      );
      return new GenerateContentResponse(taskId, contentJson);
    } catch (RuntimeException | JsonProcessingException e) {
      String finishedAt = Instant.now().toString();
      String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
      jdbc.update(
        "update generate_task set status = ?, error_message = ?, finished_at = ? where id = ?",
        "failed", message, finishedAt, taskId
      );
      throw new IllegalStateException(message, e);
    }
  }

  private String buildSystemPrompt() {
    return """
        你是 CardFlow AI 的信息卡片 HTML 设计师。
        必须输出严格 JSON,不要输出 Markdown,不要输出解释。
        """;
  }

  private String buildUserPrompt(GenerateContentRequest request) {
    if ("article".equals(request.generationMode())) {
      return buildArticlePrompt(request);
    }
    return buildTopicPrompt(request);
  }

  private String buildTopicPrompt(GenerateContentRequest request) {
    TopicInput topic = request.topicInput();
    OutputSpec output = outputSpec(request.outputFormat());
    return """
        请根据下面主题生成一张动态 HTML 信息卡片 json：
        平台规格：%s
        画布宽高：%dpx × %dpx
        内容策略：%s
        主题标题：%s
        副标题或上下文：%s
        补充说明：%s
        """.formatted(
        output.label(),
        output.width(),
        output.height(),
        output.strategy(),
        safe(topic == null ? "" : topic.title()),
        safe(topic == null ? "" : topic.subtitle()),
        safe(topic == null ? "" : topic.instructions())
    );
  }

  private String buildArticlePrompt(GenerateContentRequest request) {
    return "请根据下面文章生成 CardFlow html_card JSON：\n" + safe(request.articleInput() == null ? "" : request.articleInput().body());
  }

  private OutputSpec outputSpec(String outputFormat) {
    return switch (safe(outputFormat)) {
      case "youtube_16_9" -> new OutputSpec("YouTube 封面 16:9", 1280, 720, "优先强标题、强对比、少文字，适合作为视频封面。");
      case "bilibili_16_9" -> new OutputSpec("B站封面 16:9", 1280, 720, "优先强标题、视觉冲突和二次元/知识区封面可读性。");
      case "douyin_9_16" -> new OutputSpec("抖音竖屏封面 9:16", 900, 1600, "优先竖屏视觉冲击，标题靠中上，避免底部交互区。");
      default -> new OutputSpec("小红书知识卡片 3:4", 900, 1200, "优先知识卡片结构，可使用清单、对比、流程、概念图等信息版式。");
    };
  }

  private record OutputSpec(String label, int width, int height, String strategy) {}

  private String inputText(GenerateContentRequest request) {
    if ("article".equals(request.generationMode()) && request.articleInput() != null) {
      return safe(request.articleInput().body());
    }
    TopicInput topic = request.topicInput();
    return topic == null ? "" : safe(topic.title()) + "\n" + safe(topic.subtitle()) + "\n" + safe(topic.instructions());
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize JSON", e);
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
```

**Note:** `ChatClient.prompt()` in Spring AI 1.0.x returns a `PromptSpec` builder; the exact chain (`.system(String)`, `.user(String)`, `.call().content()`) is the public API. If the API differs slightly, adjust to match (e.g. `chatClient.prompt().system(...).user(...).call().content()`).

- [ ] **Step 10.4: Create ChatClientConfig**

Create `apps/api/src/main/java/ai/cardflow/api/llm/ChatClientConfig.java`:

```java
package ai.cardflow.api.llm;

import ai.cardflow.api.config.AppProperties;
import ai.cardflow.api.skill.SkillRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.openai.OpenAiApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient 装配。
 *
 * <p>复用 DeepSeek 的 OpenAI 兼容协议,通过自定义 base-url 接入。</p>
 */
@Configuration
public class ChatClientConfig {

  @Bean
  public ChatClient chatClient(
    OpenAiApi deepSeekApi,
    SkillRegistry registry,
    ReadSkillTool readSkillTool
  ) {
    return ChatClient.builder(deepSeekApi)
      .defaultOptions(ChatOptions.builder()
        .model("deepseek-chat")
        .temperature(0.85)
        .build())
      .defaultAdvisors(new SkillRoutingAdvisor(registry))
      .defaultTools(readSkillTool)
      .build();
  }

  @Bean
  public OpenAiApi deepSeekApi(AppProperties properties) {
    AppProperties.Llm llm = properties.llm();
    return new OpenAiApi(llm.baseUrl(), llm.apiKey());
  }
}
```

- [ ] **Step 10.5: Remove Spring AI autoconfig exclusion**

Edit `apps/api/src/main/java/ai/cardflow/api/CardflowApiApplication.java`. If Step 1.5 added `@SpringBootApplication(exclude = {org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration.class})`, **remove the exclusion** so Spring AI autoconfigures `OpenAiApi` (we still rely on it for the bean, even though we build `ChatClient` ourselves).

- [ ] **Step 10.6: Run GenerationServiceTest**

Run: `cd apps/api && mvn -q test -Dtest=GenerationServiceTest`
Expected: PASS. If mock setup needs adjustment for Spring AI's actual fluent API, tune the mocks — the implementation in Step 10.3 uses `.system(...).user(...).call().content()`.

- [ ] **Step 10.7: Run full test suite**

Run: `cd apps/api && mvn -q test`
Expected: PASS for all unit tests. The `SkillContentSanityTest` verifies the SKILL.md has the required 7 sections.

- [ ] **Step 10.8: Commit**

```bash
git add apps/api/src/main/java/ai/cardflow/api/service/GenerationService.java \
        apps/api/src/main/java/ai/cardflow/api/llm/ChatClientConfig.java \
        apps/api/src/main/java/ai/cardflow/api/CardflowApiApplication.java \
        apps/api/src/test/java/ai/cardflow/api/GenerationServiceTest.java
git commit -m "feat(llm): wire ChatClient and refactor GenerationService"
```

---

## Task 11: Delete legacy LLM Provider code

**Files:**
- Delete: `apps/api/src/main/java/ai/cardflow/api/llm/LlmProvider.java`
- Delete: `apps/api/src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java`
- Delete: `apps/api/src/test/java/ai/cardflow/api/llm/DeepSeekLlmProviderTest.java`
- Modify: `apps/api/src/main/java/ai/cardflow/api/config/AppConfig.java` (remove `RestTemplate` bean if no other consumer)

- [ ] **Step 11.1: Verify no other consumers of LlmProvider / RestTemplate**

Run:
```bash
cd apps/api && grep -r "LlmProvider" src --include="*.java"
cd apps/api && grep -r "RestTemplate" src/main --include="*.java"
```
Expected: zero hits for `LlmProvider` (other than the file being deleted) and zero hits for `RestTemplate` in `src/main`. If `RestTemplate` is still referenced, KEEP the bean for now.

- [ ] **Step 11.2: Delete the three legacy files**

```bash
cd apps/api && rm src/main/java/ai/cardflow/api/llm/LlmProvider.java \
                  src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java \
                  src/test/java/ai/cardflow/api/llm/DeepSeekLlmProviderTest.java
```

- [ ] **Step 11.3: Remove `RestTemplate` bean if no consumers**

Edit `apps/api/src/main/java/ai/cardflow/api/config/AppConfig.java`. Remove the `restTemplate()` `@Bean` method if Step 11.1 confirmed no consumers. Otherwise leave it.

- [ ] **Step 11.4: Run full test suite**

Run: `cd apps/api && mvn -q test`
Expected: PASS. All legacy references are gone.

- [ ] **Step 11.5: Verify Spring context loads**

Run: `cd apps/api && mvn -q -DskipTests spring-boot:run` in background, wait 5s, then `curl -s http://localhost:8080/actuator/health` (if actuator is configured) or any other endpoint that confirms Spring booted. Stop the process after verification.

If no easy endpoint exists, run `mvn -q -DskipTests compile` and confirm BUILD SUCCESS — that's sufficient to catch wiring errors at compile time.

- [ ] **Step 11.6: Commit**

```bash
git add -A apps/api/src/main/java/ai/cardflow/api/llm/LlmProvider.java \
          apps/api/src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java \
          apps/api/src/test/java/ai/cardflow/api/llm/DeepSeekLlmProviderTest.java \
          apps/api/src/main/java/ai/cardflow/api/config/AppConfig.java
git commit -m "refactor(llm): remove legacy LlmProvider and DeepSeekLlmProvider"
```

---

## Task 12: Final verification

- [ ] **Step 12.1: Run full test suite**

Run: `cd apps/api && mvn -q test`
Expected: PASS.

- [ ] **Step 12.2: Verify coverage ≥ 95% on new/modified classes**

Run: `cd apps/api && mvn -q verify -Pcoverage` (or however coverage is configured in this repo).
Expected: ≥ 95% line coverage on `ai.cardflow.api.skill.*`, `ai.cardflow.api.llm.SkillRoutingAdvisor`, `ai.cardflow.api.llm.ReadSkillTool`, `ai.cardflow.api.service.HtmlCardValidator`, `ai.cardflow.api.service.GenerationService`.

- [ ] **Step 12.3: Smoke test with real DeepSeek (optional)**

If `DEEPSEEK_API_KEY` is available, start the server (`cd apps/api && mvn spring-boot:run`) and POST to `/api/generate` with a topic input. Verify the response contains valid `html_card` JSON. Stop the server after.

If the key is unavailable, skip this step and rely on the integration test from Task 10 to catch any runtime issues.

- [ ] **Step 12.4: Commit any final adjustments**

If Steps 12.1-12.3 surfaced any minor fixes, commit them:

```bash
git add -A
git commit -m "chore: final verification adjustments"
```

---

## Self-Review Notes (for plan author)

**Spec coverage check:**

- §3 Storage format → Task 2
- §4.1 SkillMeta → Task 3
- §4.2 SkillRegistry interface → Task 5
- §4.3 FileSystemSkillRegistry → Task 5
- §4.4 SkillSummaryFormatter → Task 4
- §5.1 SkillRoutingAdvisor → Task 7
- §5.2 ReadSkillTool → Task 8
- §6 ChatClient config → Task 10
- §7 GenerationService refactor → Task 10
- §7.4 HtmlCardValidator → Task 9
- §8 error handling → covered in Tasks 5, 8, 9, 10
- §9.1 unit tests → Tasks 3-9, 10
- §9.3 sanity test → Task 6
- §10 migration steps → Task 0 (Spring Boot upgrade) precedes Task 1 (Spring AI deps); remaining Tasks 2-12 match spec Step 1→5

**Risk acknowledgment:** Spring Boot is upgraded from 3.3.5 to 3.5.x in Task 0 before any Spring AI dependency is added. Task 1 then uses Spring AI 1.1.x which officially supports Spring Boot 3.4.x and 3.5.x. The original 1.0.x-vs-3.3.5 incompatibility risk is resolved by the order of these tasks.

**Known API drift risk:** Spring AI 1.1.x `ChatClient`, `BaseAdvisor`, and `ChatClientRequest` APIs may have minor naming differences from the spec's pseudocode. Tasks 7, 8, 10 include API-adjustment steps (e.g., test verifies behavior, implementation may need `.system(String)` vs `.systemText(String)`, etc.). The exact API surface should be confirmed against the chosen 1.1.x version's javadoc during implementation.