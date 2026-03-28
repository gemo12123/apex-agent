package org.gemo.apex.tool.skills;

import org.gemo.apex.constant.Constant;
import org.springaicommunity.agent.utils.MarkdownParser;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CustomSkillsTool {

    private static final String TOOL_DESCRIPTION_TEMPLATE = "通过名称激活一个专用智能体技能。可用技能名称：{available_skill_names}。返回被包裹在 <activated_skill> 标签中的该技能指令。这些指令为当前任务提供专门的指导。当你识别到某个任务与某个技能的描述相匹配时，应使用该技能。只能使用 <available_skills> 部分中完全一致的技能名称。";

    private List<Skill> skills = new ArrayList<>();

    private String skillsXml;

    private ToolCallback toolCallback;

    public CustomSkillsTool(List<Skill> skills, String skillsXml, ToolCallback toolCallback) {
        this.skills = skills;
        this.skillsXml = skillsXml;
        this.toolCallback = toolCallback;
    }

    public String getSkillsXml() {
        return skillsXml;
    }

    public ToolCallback getToolCallback() {
        return toolCallback;
    }

    public static record SkillsInput(
            @ToolParam(description = "The skill name (no arguments). E.g., \"pdf\" or \"xlsx\"") String command) {
    }

    public static class SkillsFunction implements Function<SkillsInput, String> {

        private Map<String, Skill> skillsMap;

        public SkillsFunction(Map<String, Skill> skillsMap) {
            this.skillsMap = skillsMap;
        }

        @Override
        public String apply(SkillsInput input) {
            Skill skill = this.skillsMap.get(input.command());
            if (skill == null) {
                return "Skill " + input.command() + " not found. Available skills are: "
                        + String.join(", ", skillsMap.keySet());
            }

            return parseResponse(skill);
        }

        private String parseResponse(Skill skill) {
            String content = """
                    <activated_skill name="{skillName}">
                      <instructions>
                        {instructions}
                      </instructions>
                    </activated_skill>
                    """.replace("{skillName}", skill.frontMatter().get("name").toString())
                    .replace("{instructions}", skill.content());
            return content;
        }

    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private List<Skill> skills = new ArrayList<>();

        private String toolDescriptionTemplate;

        protected Builder() {

        }

        public Builder toolDescriptionTemplate(String template) {
            this.toolDescriptionTemplate = template;
            return this;
        }

        public Builder addSkillsResources(List<Resource> skillsRootPaths) {
            for (Resource skillsRootPath : skillsRootPaths) {
                this.addSkillsResource(skillsRootPath);
            }
            return this;
        }

        public Builder addSkillsResource(Resource skillsRootPath) {
            try {
                String path = skillsRootPath.getFile().toPath().toAbsolutePath().toString();
                this.addSkillsDirectory(path);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to load skills from directory: " + skillsRootPath, ex);
            }
            return this;
        }

        public Builder addSkillsDirectory(String skillsRootDirectory) {
            this.addSkillsDirectories(List.of(skillsRootDirectory));
            return this;
        }

        public Builder addSkillsDirectories(List<String> skillsRootDirectories) {
            for (String skillsRootDirectory : skillsRootDirectories) {
                try {
                    this.skills.addAll(skills(skillsRootDirectory));
                } catch (IOException ex) {
                    throw new RuntimeException("Failed to load skills from directory: " + skillsRootDirectory, ex);
                }
            }
            return this;
        }

        public CustomSkillsTool build() {
            Assert.notEmpty(this.skills, "At least one skill must be configured");

            String skillsXml = this.skills.stream().map(Skill::toXml).collect(Collectors.joining("\n"));

            this.toolDescriptionTemplate = this.toolDescriptionTemplate == null ? TOOL_DESCRIPTION_TEMPLATE
                    : this.toolDescriptionTemplate;

            FunctionToolCallback<SkillsInput, String> skillToolCallBack = FunctionToolCallback
                    .builder(Constant.SKILLS_TOOL_NAME, new SkillsFunction(toSkillsMap(this.skills)))
                    .description(this.toolDescriptionTemplate.replace("{available_skill_names}", this.skills.stream()
                            .map(Skill::frontMatter)
                            .map(item -> item.get("name"))
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .collect(Collectors.joining(","))))
                    .inputType(SkillsInput.class)
                    .build();

            return new CustomSkillsTool(this.skills, skillsXml, skillToolCallBack);
        }

    }

    /**
     * Represents a SKILL.md file with its location and parsed content.
     */
    private static record Skill(Path path, Map<String, Object> frontMatter, String content) {

        public String toXml() {
            String frontMatterXml = this.frontMatter()
                    .entrySet()
                    .stream()
                    .map(e -> "  <%s>%s</%s>".formatted(e.getKey(), e.getValue(), e.getKey()))
                    .collect(Collectors.joining("\n"));

            return "<skill>\n%s\n</skill>".formatted(frontMatterXml);
        }

    }

    private static Map<String, Skill> toSkillsMap(List<Skill> skills) {

        Map<String, Skill> skillsMap = new HashMap<>();

        for (Skill skillFile : skills) {
            skillsMap.put(skillFile.frontMatter().get("name").toString(), skillFile);
        }

        return skillsMap;
    }

    /**
     * Recursively finds all SKILL.md files in the given root directory and returns
     * their
     * parsed contents.
     */
    private static List<Skill> skills(String rootDirectory) throws IOException {
        Path rootPath = Paths.get(rootDirectory);

        if (!Files.exists(rootPath)) {
            throw new IOException("Root directory does not exist: " + rootDirectory);
        }

        if (!Files.isDirectory(rootPath)) {
            throw new IOException("Path is not a directory: " + rootDirectory);
        }

        List<Skill> skillFiles = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("SKILL.md"))
                    .forEach(path -> {
                        try {
                            String markdown = Files.readString(path, StandardCharsets.UTF_8);
                            MarkdownParser parser = new MarkdownParser(markdown);
                            skillFiles.add(new Skill(path, parser.getFrontMatter(), parser.getContent()));
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to read SKILL.md file: " + path, e);
                        }
                    });
        }

        return skillFiles;
    }

}
