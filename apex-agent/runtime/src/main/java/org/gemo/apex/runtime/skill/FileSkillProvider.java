package org.gemo.apex.runtime.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.skill.SkillResource;
import org.gemo.apex.extension.skill.SkillProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.yaml.snakeyaml.Yaml;

/** 从 classpath 或文件系统目录发现 Skill，并缓存按需加载的正文与子资源。 */
public final class FileSkillProvider implements SkillProvider {
    public static final String DEFAULT_LOCATION = "classpath:skills";

    private final String location;
    private final ClassLoader classLoader;
    private volatile Map<String, Entry> entries;

    public FileSkillProvider() {
        this(DEFAULT_LOCATION);
    }

    public FileSkillProvider(Path root) {
        this(Objects.requireNonNull(root, "root").toString());
    }

    public FileSkillProvider(String location) {
        this(location, Thread.currentThread().getContextClassLoader());
    }

    FileSkillProvider(String location, ClassLoader classLoader) {
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("Skill 加载路径不能为空");
        }
        this.location = location.trim();
        this.classLoader =
                classLoader != null ? classLoader : FileSkillProvider.class.getClassLoader();
    }

    /** 首次调用发现 Skill、解析 front matter，并建立全部子资源元信息索引。 */
    @Override
    public List<SkillMeta> loadSkills() {
        return ensureEntries().values().stream().map(entry -> entry.definition().meta()).toList();
    }

    /** 首次调用读取 instructions 并写入缓存，后续返回同一个 SkillDefinition。 */
    @Override
    public SkillDefinition loadSkill(String skillName) {
        Entry entry = entry(skillName);
        SkillDefinition definition = entry.definition();
        if (definition.instructions() == null) {
            synchronized (definition) {
                if (definition.instructions() == null) {
                    definition.cacheInstructions(readInstructions(entry.source(), skillName));
                }
            }
        }
        return definition;
    }

    /** 首次调用读取资源 content 并写入 SkillResource，后续直接返回缓存。 */
    @Override
    public String loadResource(String skillName, String resourcePath) {
        Entry entry = entry(skillName);
        String normalized = normalizeResourcePath(resourcePath);
        DiscoveredResource discovered = entry.resources().get(normalized);
        if (discovered == null) {
            throw new IllegalArgumentException("Skill 资源不存在: " + skillName + "/" + normalized);
        }
        SkillResource resource = discovered.resource();
        if (resource.content() == null) {
            synchronized (resource) {
                if (resource.content() == null) {
                    resource.cacheContent(readResource(discovered, skillName));
                }
            }
        }
        return resource.content();
    }

    @Override
    public String loadResource(String path) {
        String[] parts = resourcePath(path);
        return loadResource(parts[0], parts[1]);
    }

    private Map<String, Entry> ensureEntries() {
        Map<String, Entry> snapshot = entries;
        if (snapshot != null) {
            return snapshot;
        }
        synchronized (this) {
            if (entries == null) {
                entries = Collections.unmodifiableMap(new LinkedHashMap<>(discover()));
            }
            return entries;
        }
    }

    private Map<String, Entry> discover() {
        try {
            return location.startsWith("classpath:")
                    ? discoverClasspath(location.substring("classpath:".length()))
                    : discoverFileSystem(path(location));
        } catch (IOException e) {
            throw new IllegalArgumentException("加载 Skill 失败: " + location, e);
        }
    }

    private Map<String, Entry> discoverClasspath(String configuredRoot) throws IOException {
        String root = trimSlashes(configuredRoot);
        if (root.isBlank()) {
            throw new IllegalArgumentException("classpath Skill 路径不能为空");
        }
        Resource[] resources =
                new PathMatchingResourcePatternResolver(classLoader)
                        .getResources("classpath*:" + root + "/*/SKILL.md");
        Map<String, Entry> discovered = new LinkedHashMap<>();
        for (Resource resource :
                Stream.of(resources)
                        .sorted(Comparator.comparing(Resource::getDescription))
                        .toList()) {
            add(discovered, source(resource, root));
        }
        return discovered;
    }

    private Map<String, Entry> discoverFileSystem(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Skill 加载路径不是有效目录: " + root);
        }
        Map<String, Entry> discovered = new LinkedHashMap<>();
        try (Stream<Path> children = Files.list(root)) {
            for (Path directory :
                    children.filter(Files::isDirectory)
                            .sorted(Comparator.comparing(Path::toString))
                            .toList()) {
                if (Files.isRegularFile(directory.resolve("SKILL.md"))) {
                    add(discovered, new PathSkillSource(directory));
                }
            }
        }
        return discovered;
    }

    private void add(Map<String, Entry> discovered, SkillSource source) throws IOException {
        SkillMeta meta = readMeta(source);
        Map<String, DiscoveredResource> resources = source.discoverResources();
        Map<String, SkillResource> metadata = new LinkedHashMap<>();
        resources.forEach((path, value) -> metadata.put(path, value.resource()));
        SkillDefinition definition = new SkillDefinition(meta, metadata);
        if (discovered.putIfAbsent(meta.name(), new Entry(definition, source, resources)) != null) {
            throw new IllegalArgumentException("同一 SkillProvider 内 Skill 重名: " + meta.name());
        }
    }

    private SkillSource source(Resource resource, String classpathRoot) throws IOException {
        if (resource.isFile()) {
            return new PathSkillSource(resource.getFile().toPath().getParent());
        }
        String directory = classpathSkillDirectory(resource, classpathRoot);
        String marker = classpathRoot + "/" + directory + "/";
        String skillOrigin = origin(resource, marker);
        Resource[] candidates =
                new PathMatchingResourcePatternResolver(classLoader)
                        .getResources("classpath*:" + marker + "**/*");
        Map<String, DiscoveredResource> resources = new LinkedHashMap<>();
        for (Resource candidate : candidates) {
            if (!candidate.exists()
                    || !candidate.isReadable()
                    || "SKILL.md".equals(candidate.getFilename())
                    || !skillOrigin.equals(origin(candidate, marker))) {
                continue;
            }
            String relativePath = relativeClasspathPath(candidate, marker);
            if (!relativePath.isBlank()) {
                putResource(
                        resources,
                        relativePath,
                        mediaType(relativePath),
                        candidate::getInputStream);
            }
        }
        return new ResourceSkillSource(resource, resources);
    }

    private SkillMeta readMeta(SkillSource source) {
        try (BufferedReader reader = reader(source.openSkill())) {
            Map<String, Object> metadata = new Yaml().load(readFrontMatter(reader));
            if (metadata == null) {
                throw new IllegalArgumentException("SKILL.md 元信息不能为空");
            }
            return new SkillMeta(text(metadata, "name"), text(metadata, "description"));
        } catch (IOException e) {
            throw new IllegalArgumentException("读取 SKILL.md 元信息失败", e);
        }
    }

    private String readInstructions(SkillSource source, String skillName) {
        try (BufferedReader reader = reader(source.openSkill())) {
            readFrontMatter(reader);
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!body.isEmpty()) {
                    body.append('\n');
                }
                body.append(line);
            }
            String instructions = body.toString().strip();
            if (instructions.isBlank()) {
                throw new IllegalArgumentException("Skill instructions 不能为空: " + skillName);
            }
            return instructions;
        } catch (IOException e) {
            throw new IllegalArgumentException("读取 Skill 失败: " + skillName, e);
        }
    }

    private String readResource(DiscoveredResource resource, String skillName) {
        try (InputStream input = resource.content().open()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "读取 Skill 资源失败: " + skillName + "/" + resource.resource().path(), e);
        }
    }

    private String readFrontMatter(BufferedReader reader) throws IOException {
        if (!"---".equals(reader.readLine())) {
            throw new IllegalArgumentException("SKILL.md 必须以 YAML front matter 开始");
        }
        StringBuilder yaml = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if ("---".equals(line)) {
                return yaml.toString();
            }
            yaml.append(line).append('\n');
        }
        throw new IllegalArgumentException("SKILL.md 缺少 front matter 结束标记");
    }

    private Entry entry(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName 不能为空");
        }
        Entry entry = ensureEntries().get(skillName);
        if (entry == null) {
            throw new IllegalArgumentException("Skill 不存在: " + skillName);
        }
        return entry;
    }

    private void putResource(
            Map<String, DiscoveredResource> resources,
            String relativePath,
            String fileType,
            ResourceContent content) {
        String normalized = normalizeResourcePath(relativePath);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        DiscoveredResource resource =
                new DiscoveredResource(new SkillResource(normalized, fileName, fileType), content);
        if (resources.putIfAbsent(normalized, resource) != null) {
            throw new IllegalArgumentException("Skill 资源路径重复: " + normalized);
        }
    }

    private String normalizeResourcePath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("Skill 资源路径不能为空");
        }
        String value = resourcePath.trim().replace('\\', '/');
        if (value.startsWith("/")) {
            throw new IllegalArgumentException("Skill 资源路径必须是相对路径");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("Skill 资源路径非法: " + resourcePath);
            }
        }
        return value;
    }

    private String[] resourcePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Skill 资源路径不能为空");
        }
        int separator = path.indexOf('/');
        if (separator <= 0 || separator == path.length() - 1) {
            throw new IllegalArgumentException("Skill 资源路径必须为 skillName/relativePath");
        }
        return new String[] {path.substring(0, separator), path.substring(separator + 1)};
    }

    private String classpathSkillDirectory(Resource resource, String root) throws IOException {
        String marker = root + "/";
        String external = decoded(resource.getURL().toExternalForm());
        int start = external.lastIndexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("无法解析 classpath Skill 路径: " + resource);
        }
        String relative = external.substring(start + marker.length());
        int separator = relative.indexOf('/');
        if (separator <= 0) {
            throw new IllegalArgumentException("无法解析 classpath Skill 目录: " + resource);
        }
        return relative.substring(0, separator);
    }

    private String relativeClasspathPath(Resource resource, String marker) throws IOException {
        String external = decoded(resource.getURL().toExternalForm());
        int start = external.lastIndexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("无法解析 classpath Skill 资源路径: " + resource);
        }
        return external.substring(start + marker.length());
    }

    private String origin(Resource resource, String marker) throws IOException {
        String external = decoded(resource.getURL().toExternalForm());
        int markerIndex = external.lastIndexOf(marker);
        return markerIndex < 0 ? external : external.substring(0, markerIndex);
    }

    private String decoded(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String mediaType(String fileName) {
        String guessed = URLConnection.guessContentTypeFromName(fileName);
        return guessed != null ? guessed : "application/octet-stream";
    }

    private Path path(String value) {
        try {
            return value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Skill 加载路径非法: " + value, e);
        }
    }

    private String trimSlashes(String value) {
        String result = value.trim().replace('\\', '/');
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String text(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("SKILL.md " + key + " 不能为空");
        }
        return text;
    }

    private BufferedReader reader(InputStream input) {
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private record Entry(
            SkillDefinition definition,
            SkillSource source,
            Map<String, DiscoveredResource> resources) {
        private Entry {
            resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        }
    }

    private record DiscoveredResource(SkillResource resource, ResourceContent content) {}

    @FunctionalInterface
    private interface ResourceContent {
        InputStream open() throws IOException;
    }

    private interface SkillSource {
        InputStream openSkill() throws IOException;

        Map<String, DiscoveredResource> discoverResources() throws IOException;
    }

    private final class PathSkillSource implements SkillSource {
        private final Path root;

        private PathSkillSource(Path root) throws IOException {
            this.root = root.toRealPath();
            Path skillFile = this.root.resolve("SKILL.md");
            if (!Files.isRegularFile(skillFile) || !skillFile.toRealPath().startsWith(this.root)) {
                throw new IllegalArgumentException("SKILL.md 必须位于 Skill 目录内: " + root);
            }
        }

        @Override
        public InputStream openSkill() throws IOException {
            return open("SKILL.md");
        }

        @Override
        public Map<String, DiscoveredResource> discoverResources() throws IOException {
            Map<String, DiscoveredResource> resources = new LinkedHashMap<>();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(Files::isRegularFile)
                                .filter(path -> !path.equals(root.resolve("SKILL.md")))
                                .sorted(Comparator.comparing(Path::toString))
                                .toList()) {
                    Path real = file.toRealPath();
                    if (!real.startsWith(root)) {
                        throw new IllegalArgumentException("Skill 资源越过目录边界: " + file);
                    }
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    String detected = Files.probeContentType(file);
                    putResource(
                            resources,
                            relative,
                            detected != null ? detected : mediaType(relative),
                            () -> open(relative));
                }
            }
            return resources;
        }

        private InputStream open(String relativePath) throws IOException {
            Path candidate = root.resolve(relativePath).normalize();
            if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("Skill 资源不存在: " + relativePath);
            }
            Path real = candidate.toRealPath();
            if (!real.startsWith(root)) {
                throw new IllegalArgumentException("Skill 资源越过目录边界: " + relativePath);
            }
            return Files.newInputStream(real);
        }
    }

    private static final class ResourceSkillSource implements SkillSource {
        private final Resource skillFile;
        private final Map<String, DiscoveredResource> resources;

        private ResourceSkillSource(Resource skillFile, Map<String, DiscoveredResource> resources) {
            this.skillFile = skillFile;
            this.resources = Collections.unmodifiableMap(new LinkedHashMap<>(resources));
        }

        @Override
        public InputStream openSkill() throws IOException {
            return skillFile.getInputStream();
        }

        @Override
        public Map<String, DiscoveredResource> discoverResources() {
            return resources;
        }
    }
}
