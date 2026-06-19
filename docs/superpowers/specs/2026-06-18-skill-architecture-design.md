# CardFlow AI Skill Architecture Design

Date: 2026-06-18
Status: Approved design, pending implementation plan

## 1. Goal

Replace the hardcoded DeepSeek prompt in `DeepSeekLlmProvider` with a Skill-based architecture that:

- Externalizes prompt content into versionable Markdown files (`SKILL.md`)
- Exposes Skills as callable tools so the LLM can load detailed rules on demand
- Uses Spring AI standard `ChatClient` / `BaseAdvisor` / `ToolCallback` abstractions
- Leaves room for future Skills (article mode, 9 cover styles) without restructuring code

This is an internal architecture change. HTTP API contracts, database schema, and user-facing behavior stay the same.

## 2. Architecture Overview

Three layers:

1. **Domain layer** (no Spring AI dependency): `SkillMeta`, `SkillRegistry`, `FileSystemSkillRegistry`, `SkillSummaryFormatter`
2. **Spring AI adapter layer**: `SkillRoutingAdvisor` (BaseAdvisor), `ReadSkillTool` (ToolCallback), `ChatClientConfig`
3. **Existing service layer**: `GenerationService` refactored to use `ChatClient` directly; `LlmProvider` / `DeepSeekLlmProvider` removed

Call flow:

```
GenerationService.generate(req)
  └─> chatClient.prompt()
        .system(businessSystemPrompt)        // GenerationService injects CardFlow business protocol
        .user(businessUserPrompt)            // GenerationService injects topic + platform spec
        .call()
        └─> SkillRoutingAdvisor.before()     // appends Skills summary list to system
              └─> ChatClient.call()          // sends to DeepSeek via OpenAI-compatible API
        └─> DeepSeek may call read_skill     // LLM-triggered tool call
              └─> ReadSkillTool.call()       // returns SKILL.md full content
        └─> ChatClient returns content       // GenerationService validates & stores
```

## 3. Skill Storage Format

Each Skill is a directory under `apps/api/src/main/resources/skills/<name>/` containing:

### 3.1 `meta.yaml`

```yaml
name: cardflow.html-card-generator
description: 生成符合 CardFlow html_card 协议的动态信息卡(小红书/抖音/B站/YouTube)
whenToApply: 用户调用内容生成接口、generationMode=topic 时
tags:
  - topic
  - html_card
version: 1
```

Required fields: `name`, `description`, `whenToApply`.
Optional fields: `tags` (list of strings), `version` (string).

### 3.2 `SKILL.md`

Free-form Markdown containing the full prompt body. For `cardflow.html-card-generator`, this is the concatenation of the seven existing sections in `DeepSeekLlmProvider`:

- 角色 (identity)
- 输出协议 (output protocol)
- 内容分析 (content analysis)
- 布局选择 (layout selection)
- 视觉风格 (visual style)
- HTML 约束 (HTML constraints)
- 质量约束 (quality gate)

### 3.3 Naming convention

Skill directory name MUST equal `meta.yaml.name`. Names MUST match `^[a-z0-9]+(\.[a-z0-9-]+)+$` (e.g. `cardflow.html-card-generator`, `cardflow.article-generator`).

## 4. Domain Layer

### 4.1 `SkillMeta`

```java
public record SkillMeta(
    String name,
    String description,
    String whenToApply,
    List<String> tags,
    String version,
    Resource contentResource
) {}
```

### 4.2 `SkillRegistry`

```java
public interface SkillRegistry {
    List<SkillMeta> all();
    Optional<SkillMeta> find(String name);
    String summary();
    String readFullContent(String name);
}
```

### 4.3 `FileSystemSkillRegistry`

```java
@Component
public class FileSystemSkillRegistry implements SkillRegistry {
    private final Map<String, SkillMeta> skills;

    public FileSystemSkillRegistry(
        ResourceLoader loader,
        ObjectMapper yamlMapper,
        SkillSummaryFormatter formatter
    ) {
        this.skills = loadFromClasspath("skills/**/meta.yaml", loader, yamlMapper);
    }

    @Override public List<SkillMeta> all() { return List.copyOf(skills.values()); }
    @Override public Optional<SkillMeta> find(String name) { return Optional.ofNullable(skills.get(name)); }
    @Override public String summary() { return formatter.format(all()); }

    @Override
    public String readFullContent(String name) {
        SkillMeta meta = skills.get(name);
        if (meta == null) throw new IllegalArgumentException("Unknown skill: " + name);
        try {
            return StreamUtils.copyToString(meta.contentResource().getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read SKILL.md for " + name, e);
        }
    }
}
```

Startup-time validation (fail-fast, throws `IllegalStateException` if violated):

1. Every `meta.yaml` contains `name`, `description`, `whenToApply`
2. Directory name equals `meta.yaml.name`
3. Each Skill directory contains a non-empty `SKILL.md`
4. Names are globally unique
5. Names match `^[a-z0-9]+(\.[a-z0-9-]+)+$`

### 4.4 `SkillSummaryFormatter`

Formats `List<SkillMeta>` into a bullet list:

```
- cardflow.html-card-generator: 生成符合 CardFlow html_card 协议的动态信息卡(小红书/抖音/B站/YouTube)
- cardflow.article-generator: 把长文拆解为信息卡片大纲(...)
```

## 5. Spring AI Adapter Layer

### 5.1 `SkillRoutingAdvisor`

```java
public class SkillRoutingAdvisor implements BaseAdvisor {
    private static final String SKILLS_HEADER = """

        ## 可用 Skills

        以下 Skills 可用。需要详细规则时，调用 read_skill 工具读取完整 SKILL.md。

        %s

        """;

    private final SkillRegistry registry;

    public SkillRoutingAdvisor(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int getOrder() { return 0; }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String summary = registry.summary();
        String enriched = request.prompt().getSystemMessage().getText()
            + SKILLS_HEADER.formatted(summary);
        return request.mutate()
            .prompt(request.prompt().augmentSystemMessage(enriched))
            .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        return response;
    }
}
```

`getOrder() = 0` so it runs before any future Advisors (logging, token counting, etc.).

### 5.2 `ReadSkillTool`

```java
@Component
public class ReadSkillTool implements ToolCallback {
    private final SkillRegistry registry;
    private final String name = "read_skill";
    private final String description = "按需读取某个 Skill 的完整 SKILL.md 内容,获取详细的协议/约束/风格规则";
    private final String inputSchema = """
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

    public ReadSkillTool(SkillRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String call(String jsonArguments) {
        // Parse {"name": "..."}; on success return registry.readFullContent(name)
        // On unknown Skill: return `{"error":"Unknown skill: <name>"}` JSON
        // On JSON parse failure: return `{"error":"Invalid arguments"}` JSON
        // Never throw - let LLM see error and recover in next turn
    }
}
```

Tool failures return error JSON, never throw. This is intentional: a failed tool call is part of the LLM's reasoning cycle, not a system error.

## 6. ChatClient Configuration

### 6.1 `ChatClientConfig`

```java
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

Uses `spring-ai-openai` with custom `base-url` pointing at DeepSeek (OpenAI-compatible protocol). No `spring-ai-deepseek` dependency needed.

### 6.2 `application.yml` additions

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

## 7. GenerationService Refactor

### 7.1 New dependencies

```java
public GenerationService(
    JdbcTemplate jdbc,
    AppProperties properties,
    ChatClient chatClient,
    HtmlCardValidator validator
) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.chatClient = chatClient;
    this.validator = validator;
}
```

### 7.2 `generate()` flow

```java
public GenerateContentResponse generate(GenerateContentRequest request) {
    String taskId = UUID.randomUUID().toString();
    recordTaskStart(taskId, request);

    try {
        String contentJson = chatClient.prompt()
            .system(buildSystemPrompt())
            .user(buildUserPrompt(request))
            .call()
            .content();

        validator.validate(objectMapper.readTree(contentJson));
        recordTaskSuccess(taskId, contentJson, "deepseek-chat");
        return new GenerateContentResponse(taskId, contentJson);
    } catch (RuntimeException e) {
        recordTaskFailure(taskId, e.getMessage());
        throw e;
    }
}
```

### 7.3 Prompt split

- `buildSystemPrompt()` returns **business-level system text only**: output JSON protocol contract, design principles. The seven detailed sections (identity, content analysis, layout, visual style, HTML constraints, quality gate) move to `SKILL.md`.
- `buildUserPrompt()` takes over the existing `topicPrompt` + `outputSpec` logic: platform spec, canvas dimensions, topic fields.

### 7.4 `HtmlCardValidator`

Extracted from `DeepSeekLlmProvider.validateHtmlCard` / `assertSafeHtml`. Pure validation logic:

- `kind` must be `html_card`
- `title` non-blank
- `html` non-blank and contains `<html>` ... `</html>`
- HTML must not contain `<script>`, `http://`, `https://`, `src=`, `href=`, `@import`, `url(`

Throws `IllegalStateException` on violation.

## 8. Error Handling

| Error source | Where thrown | Handling |
|--------------|--------------|----------|
| Skill loading failure (missing meta.yaml, malformed YAML, missing SKILL.md, name conflict) | `FileSystemSkillRegistry` constructor | **Fail-fast**: `IllegalStateException`, application fails to start. Skills are part of the contract; no degraded mode. |
| LLM returns invalid JSON or violates html_card protocol | `HtmlCardValidator.validate()` | `IllegalStateException` → task marked `failed`, error stored in `generate_task.error_message` |
| DeepSeek call failure (5xx, network timeout, bad API key) | Spring AI `ChatClient.call()` throws `RestClientException` | Caught by `GenerationService` → task `failed` → error message returned to client |
| `read_skill` tool called with unknown Skill name | `ReadSkillTool.call()` returns `{"error": "Unknown skill: xxx"}` JSON | No exception thrown. LLM sees error in next turn and can retry or proceed. |

## 9. Testing Strategy

### 9.1 Unit tests

| Test class | Coverage | Mocking |
|------------|----------|---------|
| `FileSystemSkillRegistryTest` | Startup scan, meta.yaml parsing, SKILL.md association, name conflict fail-fast | Use `@TempDir` to construct temporary skill directories |
| `SkillRoutingAdvisorTest` | `before()` appends summary correctly, `after()` passes through | Mock `SkillRegistry` |
| `ReadSkillToolTest` | Known Skill returns content, unknown returns error JSON, parse failure returns error JSON | Mock `SkillRegistry` |
| `HtmlCardValidatorTest` | All violation cases (missing kind/title/html, contains script, contains external URL) | None |
| `GenerationServiceTest` (rewritten) | Business orchestration: create task, call ChatClient, record usage, error handling | Mock `ChatClient` |

### 9.2 Integration tests

| Test class | Coverage | Trigger |
|------------|----------|---------|
| `SkillIntegrationTest` | Real DeepSeek call, summary + read_skill chain produces valid html_card JSON | `@Tag("integration")`, run with `RUN_INTEGRATION=1` env var |

### 9.3 Sanity test

`SkillContentSanityTest` runs at test time and asserts `cardflow.html-card-generator/SKILL.md` contains required keywords (`html_card`, `designNotes`, `禁止 script`, etc.). This compensates for losing the implicit "7 sections present" check that `DeepSeekLlmProviderTest` provided.

### 9.4 Coverage target

Maintain ≥ 95% line coverage on new and modified classes.

## 10. Migration Steps

Run in order; each step is independently verifiable.

1. **Add dependencies**: Add `spring-ai-openai-spring-boot-starter` and `jackson-dataformat-yaml` to `apps/api/pom.xml`. Add `spring.ai.openai` block to `application.yml`. Run existing tests to confirm Spring boots.
2. **Extract Skill files and Registry**: Create `resources/skills/cardflow.html-card-generator/SKILL.md` and `meta.yaml`. Copy the 7 sections from `DeepSeekLlmProvider` into `SKILL.md`. Add `SkillMeta`, `SkillRegistry`, `FileSystemSkillRegistry`, `SkillSummaryFormatter` and their tests. Verify registry loads correctly. DeepSeekLlmProvider still active; no behavior change.
3. **Add SkillRoutingAdvisor and ReadSkillTool**: Create both classes with tests. Do NOT wire to ChatClient yet. Old business path still uses DeepSeekLlmProvider.
4. **Wire ChatClient and refactor GenerationService**: Create `ChatClientConfig`, `HtmlCardValidator`. Change `GenerationService` to inject `ChatClient` + `HtmlCardValidator`. Update `GenerationServiceTest` to mock `ChatClient`. Verify end-to-end generation produces equivalent html_card JSON.
5. **Delete old code**: Remove `LlmProvider.java`, `DeepSeekLlmProvider.java`, `DeepSeekLlmProviderTest.java`. Remove `RestTemplate` bean from `AppConfig` if unused elsewhere. Mark `AppProperties.llm.provider` as `@Deprecated` (keep field for now). Run full test suite + type check + one real generation to confirm SKILL.md content is being used.

## 11. Risks and Mitigations

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Spring AI version incompatible with Spring Boot 3.x | Medium | Step 1 isolated verification; run full test suite after pom change |
| DeepSeek `response_format: json_object` parameter differs in OpenAI-compatible mode | Low | Verify in Step 4; use `ChatOptions.responseFormat(...)` if needed |
| Model output drifts after reading SKILL.md (missing fields, extra markdown) | Medium | Compare generated JSON samples with pre-refactor output; `HtmlCardValidator` enforces protocol |
| LLM calls `read_skill` excessively, increasing latency and cost | Medium | Skill summaries include precise `whenToApply` text so LLM only calls when detailed rules are needed; monitor `usage_record` token counts |
| Single Skill content too large, blows context window | Low | Target ≤ 4KB per `SKILL.md`; add size assertion in `SkillContentSanityTest` |
| Loss of direct coverage of 7 sections in old test | Medium | `SkillContentSanityTest` asserts required keywords present in `SKILL.md` |

## 12. Out of Scope

Explicitly excluded from this design:

- Concrete content for the 9 cover styles (skeleton only; future spec)
- `article` generation mode
- Skill hot reload (startup-time loading only)
- Skill A/B testing / gradual rollout
- Multiple LLM Providers coexisting
- Prompt version management / rollback (git-based for now)

## 13. Files Affected

### New

- `apps/api/src/main/resources/skills/cardflow.html-card-generator/SKILL.md`
- `apps/api/src/main/resources/skills/cardflow.html-card-generator/meta.yaml`
- `apps/api/src/main/java/ai/cardflow/api/skill/SkillMeta.java`
- `apps/api/src/main/java/ai/cardflow/api/skill/SkillRegistry.java`
- `apps/api/src/main/java/ai/cardflow/api/skill/FileSystemSkillRegistry.java`
- `apps/api/src/main/java/ai/cardflow/api/skill/SkillSummaryFormatter.java`
- `apps/api/src/main/java/ai/cardflow/api/llm/SkillRoutingAdvisor.java`
- `apps/api/src/main/java/ai/cardflow/api/llm/ReadSkillTool.java`
- `apps/api/src/main/java/ai/cardflow/api/llm/ChatClientConfig.java`
- `apps/api/src/main/java/ai/cardflow/api/service/HtmlCardValidator.java`
- `apps/api/src/test/java/ai/cardflow/api/skill/FileSystemSkillRegistryTest.java`
- `apps/api/src/test/java/ai/cardflow/api/skill/SkillSummaryFormatterTest.java`
- `apps/api/src/test/java/ai/cardflow/api/llm/SkillRoutingAdvisorTest.java`
- `apps/api/src/test/java/ai/cardflow/api/llm/ReadSkillToolTest.java`
- `apps/api/src/test/java/ai/cardflow/api/service/HtmlCardValidatorTest.java`
- `apps/api/src/test/java/ai/cardflow/api/skill/SkillContentSanityTest.java`

### Modified

- `apps/api/pom.xml` (add Spring AI + jackson-dataformat-yaml)
- `apps/api/src/main/resources/application.yml` (add spring.ai.openai config)
- `apps/api/src/main/java/ai/cardflow/api/service/GenerationService.java` (refactor to use ChatClient)
- `apps/api/src/test/java/ai/cardflow/api/GenerationServiceTest.java` (mock ChatClient)

### Deleted

- `apps/api/src/main/java/ai/cardflow/api/llm/LlmProvider.java`
- `apps/api/src/main/java/ai/cardflow/api/llm/DeepSeekLlmProvider.java`
- `apps/api/src/test/java/ai/cardflow/api/llm/DeepSeekLlmProviderTest.java`