package org.gemo.apex.runtime.skill;

import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import org.gemo.apex.common.skill.*;
import org.gemo.apex.extension.skill.SkillProvider;

public final class FileSkillProvider implements SkillProvider {
    private final List<SkillDefinition> skills;

    public FileSkillProvider(Path root) {
        try (Stream<Path> dirs = Files.list(root)) {
            skills =
                    dirs.filter(Files::isDirectory)
                            .filter(d -> Files.exists(d.resolve("SKILL.md")))
                            .map(this::load)
                            .toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("加载 Skill 失败: " + root, e);
        }
    }

    private SkillDefinition load(Path dir) {
        try {
            String md = Files.readString(dir.resolve("SKILL.md"));
            String name = value(md, "name");
            String desc = value(md, "description");
            int end = md.indexOf("\n---", 3);
            String body = end < 0 ? md : md.substring(end + 4).trim();
            Map<String, SkillResourceDescriptor> resources = new LinkedHashMap<>();
            try (Stream<Path> fs = Files.walk(dir)) {
                fs.filter(Files::isRegularFile)
                        .filter(p -> !p.getFileName().toString().equals("SKILL.md"))
                        .filter(p -> !dir.relativize(p).startsWith("scripts"))
                        .forEach(
                                p -> {
                                    String n = dir.relativize(p).toString().replace('\\', '/');
                                    resources.put(
                                            n,
                                            new SkillResourceDescriptor(
                                                    n, "text/plain", p.toUri().toString()));
                                });
            }
            return new SkillDefinition(name, desc, body, resources);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析 Skill 失败: " + dir, e);
        }
    }

    private String value(String md, String key) {
        return md.lines()
                .filter(l -> l.startsWith(key + ":"))
                .map(l -> l.substring(key.length() + 1).trim())
                .findFirst()
                .orElseThrow();
    }

    public List<SkillDefinition> loadSkills() {
        return skills;
    }
}
