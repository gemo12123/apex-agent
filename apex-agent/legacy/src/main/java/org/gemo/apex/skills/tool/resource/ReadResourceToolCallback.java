package org.gemo.apex.skills.tool.resource;

import org.gemo.apex.skills.definition.skill.Skill;
import org.gemo.apex.skills.definition.resource.SkillResource;
import org.gemo.apex.skills.tool.AbstractSkillToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ReadResourceToolCallback extends AbstractSkillToolCallback {

    private final ReadResourceToolConfig config;
    private final Map<String, Skill> skillsByName;

    public ReadResourceToolCallback(ReadResourceToolConfig config, Map<String, Skill> skillsByName,
            String relativePathDescription) {
        super(
                ToolDefinition.builder()
                        .name(config.name)
                        .description(config.description)
                        .inputSchema(twoStringInputSchema(
                                config.skillNameParameterName,
                                config.skillNameParameterDescription,
                                config.relativePathParameterName,
                                relativePathDescription))
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
        String skillName = getRequiredArgument(config.skillNameParameterName, arguments);
        String relativePath = getRequiredArgument(config.relativePathParameterName, arguments);

        Skill skill = skillsByName.get(skillName);
        if (skill == null) {
            throw new IllegalArgumentException("There is no skill with name '" + skillName + "'");
        }

        List<SkillResource> matches = skill.resources().stream()
                .filter(resource -> resource.relativePath().equals(relativePath))
                .toList();
        if (matches.isEmpty()) {
            String availableResources = skill.resources().stream()
                    .map(resource -> "'" + resource.relativePath() + "'")
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "There is no resource for skill '" + skillName + "' with the path '" + relativePath
                            + "'. Available resources: [" + availableResources + "]");
        }

        return matches.getFirst().content();
    }
}
