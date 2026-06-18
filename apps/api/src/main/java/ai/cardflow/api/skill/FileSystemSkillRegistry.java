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
    List<Path> metaFiles = discoverMetaFiles(loader, fileSystemRoot, pattern);
    metaFiles.sort((a, b) -> a.getParent().getFileName().toString().compareTo(b.getParent().getFileName().toString()));
    for (Path metaFile : metaFiles) {
      SkillMeta meta = parseMeta(yamlMapper, metaFile);
      if (map.containsKey(meta.name())) {
        throw new IllegalStateException("Duplicate skill name: " + meta.name());
      }
      validateMeta(meta, metaFile);
      map.put(meta.name(), meta);
    }
    return map;
  }

  private List<Path> discoverMetaFiles(ResourceLoader loader, String fileSystemRoot, String pattern) {
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
    } catch (IOException e) {
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
      String version = String.valueOf(raw.getOrDefault("version", "1"));
      if (name == null || description == null || whenToApply == null) {
        throw new IllegalStateException("Skill meta.yaml missing required field: " + metaFile);
      }
      Path skillDir = metaFile.getParent();
      Path skillMd = skillDir.resolve("SKILL.md");
      if (!Files.exists(skillMd) || Files.size(skillMd) == 0) {
        throw new IllegalStateException("Missing SKILL.md at " + skillMd);
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
  }
}