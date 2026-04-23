package org.gemo.apex.tool.skills;

import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.StreamSupport.stream;

public final class FileSystemSkillLoader {

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(YamlFrontMatterExtension.create()))
            .build();

    private FileSystemSkillLoader() {
    }

    public static List<FileSystemSkill> loadSkills(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(dir -> Files.exists(dir.resolve("SKILL.md")))
                    .map(FileSystemSkillLoader::loadSkill)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skills from " + directory, e);
        }
    }

    public static FileSystemSkill loadSkill(Path skillDirectory) {
        Path skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in " + skillDirectory);
        }

        try {
            String markdown = Files.readString(skillFile);
            Map<String, List<String>> frontMatter = parseFrontMatter(markdown);
            List<DefaultSkillResource> resources = loadResources(skillDirectory);

            return FileSystemSkill.builder()
                    .name(getSingle(frontMatter, "name"))
                    .description(getSingle(frontMatter, "description"))
                    .content(extractContent(markdown))
                    .resources(resources)
                    .basePath(skillDirectory)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill from " + skillDirectory, e);
        }
    }

    private static Map<String, List<String>> parseFrontMatter(String markdown) {
        Node document = PARSER.parse(markdown);
        YamlFrontMatterVisitor visitor = new YamlFrontMatterVisitor();
        document.accept(visitor);
        return visitor.getData();
    }

    private static String extractContent(String markdown) {
        if (markdown.startsWith("---")) {
            int secondDelimiter = markdown.indexOf("\n---", 3);
            if (secondDelimiter != -1) {
                return markdown.substring(secondDelimiter + 4).trim();
            }
        }
        return markdown.trim();
    }

    private static String getSingle(Map<String, List<String>> map, String key) {
        return map.getOrDefault(key, List.of()).stream().findFirst().orElse(null);
    }

    private static List<DefaultSkillResource> loadResources(Path skillDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(skillDirectory)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("SKILL.md"))
                    .filter(path -> !skillDirectory.relativize(path).startsWith("scripts"))
                    .map(path -> toSkillResource(skillDirectory, path))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    private static DefaultSkillResource toSkillResource(Path skillDirectory, Path path) {
        try {
            String content = Files.readString(path);
            if (content == null || content.isBlank()) {
                return null;
            }
            String relativePath = stream(skillDirectory.relativize(path).spliterator(), false)
                    .map(Path::toString)
                    .collect(joining("/"));
            return SkillResource.builder()
                    .relativePath(relativePath)
                    .content(content)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load skill resource from " + path, e);
        }
    }
}
