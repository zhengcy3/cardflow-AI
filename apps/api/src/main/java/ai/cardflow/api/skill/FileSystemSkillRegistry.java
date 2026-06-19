package ai.cardflow.api.skill;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;
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
  @Autowired
  public FileSystemSkillRegistry(
    ResourceLoader loader,
    Yaml yamlReader,
    SkillSummaryFormatter formatter
  ) {
    this(loader, yamlReader, formatter, "classpath:skills/**/meta.yaml", null);
  }

  /**
   * 测试用构造函数,允许传入文件系统根目录和 glob 模式。
   */
  public FileSystemSkillRegistry(
    ResourceLoader loader,
    Yaml yamlReader,
    SkillSummaryFormatter formatter,
    String classpathPattern,
    String fileSystemRoot
  ) {
    this.formatter = formatter;
    this.skills = loadFromClasspath(loader, yamlReader, classpathPattern, fileSystemRoot);
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
    Yaml yamlReader,
    String pattern,
    String fileSystemRoot
  ) {
    Map<String, SkillMeta> map = new LinkedHashMap<>();
    List<SkillSource> sources = discoverMetaFiles(loader, fileSystemRoot, pattern);
    sources.sort((a, b) -> a.dirName().compareTo(b.dirName()));
    for (SkillSource source : sources) {
      SkillMeta meta = parseMeta(yamlReader, source.resource());
      if (map.containsKey(meta.name())) {
        throw new IllegalStateException("Duplicate skill name: " + meta.name());
      }
      validateMeta(meta, source.dirName());
      map.put(meta.name(), meta);
    }
    return map;
  }

  /** 一次扫描结果:meta.yaml 的 Resource + 它所在的 Skill 目录名。 */
  private record SkillSource(Resource resource, String dirName) {}

  private List<SkillSource> discoverMetaFiles(ResourceLoader loader, String fileSystemRoot, String pattern) {
    if (fileSystemRoot != null) {
      try {
        List<SkillSource> result = new ArrayList<>();
        Path root = Path.of(fileSystemRoot);
        if (!Files.exists(root)) {
          throw new IllegalStateException("Skill root not found: " + fileSystemRoot);
        }
        try (var stream = Files.walk(root)) {
          stream.filter(p -> p.getFileName().toString().equals("meta.yaml"))
            .forEach(p -> {
              try {
                result.add(new SkillSource(
                  new UrlResource(p.toUri()),
                  p.getParent().getFileName().toString()
                ));
              } catch (IOException e) {
                throw new IllegalStateException("Failed to read skill resource: " + p, e);
              }
            });
        }
        return result;
      } catch (IOException e) {
        throw new IllegalStateException("Failed to scan skills directory: " + fileSystemRoot, e);
      }
    }
    // Production path: PathMatchingResourcePatternResolver handles both filesystem
    // classpath (IDE / mvn spring-boot:run) and jar:nested: classpath (fat JAR).
    List<SkillSource> result = new ArrayList<>();
    try {
      PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(loader);
      Resource[] resources = resolver.getResources("classpath:skills/**/meta.yaml");
      for (Resource resource : resources) {
        if (!resource.exists()) {
          continue;
        }
        // Extract parent dir name from URL like ".../skills/cardflow.html-card-generator/meta.yaml"
        String url = resource.getURL().toString();
        int skillsIdx = url.lastIndexOf("/skills/");
        String dirName = "unknown";
        if (skillsIdx >= 0) {
          String after = url.substring(skillsIdx + "/skills/".length());
          int slash = after.indexOf('/');
          if (slash > 0) {
            dirName = after.substring(0, slash);
          }
        }
        result.add(new SkillSource(resource, dirName));
      }
      return result;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to scan classpath skills", e);
    }
  }

  private SkillMeta parseMeta(Yaml yamlReader, Resource metaResource) {
    try (InputStream in = metaResource.getInputStream()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> raw = yamlReader.load(in);
      String name = (String) raw.get("name");
      String description = (String) raw.get("description");
      String whenToApply = (String) raw.get("whenToApply");
      @SuppressWarnings("unchecked")
      List<String> tags = (List<String>) raw.getOrDefault("tags", List.of());
      String version = String.valueOf(raw.getOrDefault("version", "1"));
      if (name == null || description == null || whenToApply == null) {
        throw new IllegalStateException("Skill meta.yaml missing required field: " + metaResource);
      }
      String metaUrl = metaResource.getURL().toString();
      String skillMdUrl = metaUrl.substring(0, metaUrl.lastIndexOf('/') + 1) + "SKILL.md";
      Resource skillMdResource = new UrlResource(skillMdUrl);
      if (!skillMdResource.exists() || skillMdResource.contentLength() == 0) {
        throw new IllegalStateException("Missing SKILL.md at " + skillMdUrl);
      }
      return new SkillMeta(name, description, whenToApply, tags, version, skillMdResource);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to parse meta.yaml: " + metaResource, e);
    }
  }

  private void validateMeta(SkillMeta meta, String dirName) {
    if (!dirName.equals(meta.name())) {
      throw new IllegalStateException(
        "Skill directory name " + dirName + " does not match directory name in meta.yaml: " + meta.name()
      );
    }
    if (!NAME_PATTERN.matcher(meta.name()).matches()) {
      throw new IllegalStateException("Invalid skill name (must match " + NAME_PATTERN.pattern() + "): " + meta.name());
    }
  }
}