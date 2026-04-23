package org.gemo.apex.tool.skills;

import org.springframework.ai.tool.StaticToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Skills {

    private final List<Skill> skills;
    private final ToolCallbackProvider toolCallbackProvider;
    private final ToolCallback[] toolCallbacks;
    private final String formattedAvailableSkills;

    private Skills(Builder builder) {
        this.skills = List.copyOf(Validators.notEmpty(builder.skills, "skills"));

        Map<String, Skill> skillsByName = new LinkedHashMap<>();
        for (Skill skill : this.skills) {
            Skill previous = skillsByName.put(skill.name(), skill);
            if (previous != null) {
                throw new IllegalStateException("Duplicate skill name detected: '" + skill.name() + "'");
            }
        }

        ActivateSkillToolConfig activateConfig = builder.activateSkillToolConfig == null
                ? ActivateSkillToolConfig.builder().build()
                : builder.activateSkillToolConfig;

        List<ToolCallback> callbacks = new ArrayList<>();
        callbacks.add(new ActivateSkillToolCallback(activateConfig, skillsByName));

        boolean hasResources = this.skills.stream().anyMatch(skill -> !skill.resources().isEmpty());
        if (hasResources) {
            ReadResourceToolConfig readConfig = builder.readResourceToolConfig == null
                    ? ReadResourceToolConfig.builder().build()
                    : builder.readResourceToolConfig;
            String relativePathDescription = readConfig.relativePathParameterDescription != null
                    ? readConfig.relativePathParameterDescription
                    : readConfig.relativePathParameterDescriptionProvider.apply(this.skills);
            callbacks.add(new ReadResourceToolCallback(readConfig, skillsByName, relativePathDescription));
        }

        this.toolCallbacks = callbacks.toArray(ToolCallback[]::new);
        this.toolCallbackProvider = new StaticToolCallbackProvider(this.toolCallbacks);
        this.formattedAvailableSkills = formatAvailableSkills(this.skills);
    }

    public static Skills from(Collection<? extends Skill> skills) {
        return builder().skills(skills).build();
    }

    public static Skills from(Skill... skills) {
        return builder().skills(List.of(skills)).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ToolCallback[] toolCallbacks() {
        return toolCallbacks.clone();
    }

    public ToolCallbackProvider toolCallbackProvider() {
        return toolCallbackProvider;
    }

    public String formatAvailableSkills() {
        return formattedAvailableSkills;
    }

    private static String formatAvailableSkills(Collection<? extends Skill> skills) {
        StringBuilder sb = new StringBuilder();
        sb.append("<available_skills>\n");
        for (Skill skill : skills) {
            sb.append("<skill>\n")
                    .append("<name>")
                    .append(escapeXml(skill.name()))
                    .append("</name>\n")
                    .append("<description>")
                    .append(escapeXml(skill.description()))
                    .append("</description>\n")
                    .append("</skill>\n");
        }
        sb.append("</available_skills>");
        return sb.toString();
    }

    private static String escapeXml(String input) {
        if (input == null) {
            return null;
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public static final class Builder {

        private Collection<? extends Skill> skills;
        private ActivateSkillToolConfig activateSkillToolConfig;
        private ReadResourceToolConfig readResourceToolConfig;

        public Builder skills(Collection<? extends Skill> skills) {
            this.skills = skills;
            return this;
        }

        public Builder skills(Skill... skills) {
            this.skills = List.of(skills);
            return this;
        }

        public Builder activateSkillToolConfig(ActivateSkillToolConfig activateSkillToolConfig) {
            this.activateSkillToolConfig = activateSkillToolConfig;
            return this;
        }

        public Builder readResourceToolConfig(ReadResourceToolConfig readResourceToolConfig) {
            this.readResourceToolConfig = readResourceToolConfig;
            return this;
        }

        public Skills build() {
            return new Skills(this);
        }
    }
}
