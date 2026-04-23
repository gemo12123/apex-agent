package org.gemo.apex.tool.skills;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;
import java.util.stream.Collectors;

final class ActivateSkillToolCallback extends AbstractSkillToolCallback {

    private final ActivateSkillToolConfig config;
    private final Map<String, Skill> skillsByName;

    ActivateSkillToolCallback(ActivateSkillToolConfig config, Map<String, Skill> skillsByName) {
        super(
                ToolDefinition.builder()
                        .name(config.name)
                        .description(config.descriptionTemplate.replace("{available_skill_names}",
                                skillsByName.keySet().stream().collect(Collectors.joining(","))))
                        .inputSchema(singleStringInputSchema(config.parameterName, config.parameterDescription))
                        .build(),
                ToolMetadata.builder()
                        .returnDirect(config.returnDirect)
                        .build());
        this.config = config;
        this.skillsByName = Map.copyOf(skillsByName);
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> arguments = parseArguments(toolInput);
        String skillName = getRequiredArgument(config.parameterName, arguments);
        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            String availableSkillNames = skillsByName.keySet().stream()
                    .map(name -> "'" + name + "'")
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "There is no skill with name '" + skillName + "'. Available skills: [" + availableSkillNames + "]");
        }
        return """
                <activated_skill name="%s">
                  <instructions>
                    %s
                  </instructions>
                </activated_skill>
                """.formatted(skill.name(), skill.content());
    }
}
