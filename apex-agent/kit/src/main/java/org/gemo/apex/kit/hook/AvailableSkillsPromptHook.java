package org.gemo.apex.kit.hook;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.gemo.apex.common.agent.PromptDefinition;
import org.gemo.apex.common.agent.ReplacePrompt;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.AgentBuildContext;
import org.gemo.apex.common.hook.result.AgentBuildHookResult;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.skill.SkillProvider;

/** 将 Agent 启用的 Skill 元信息填充到系统提示词的 {@code {skills}} 占位符。 */
public final class AvailableSkillsPromptHook
        implements LifecycleHook<AgentBuildContext, AgentBuildHookResult> {
    public static final String REGISTRATION_NAME = "availableSkillsPromptHook";
    private static final String PLACEHOLDER = "{skills}";
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.AGENT_BUILD, AgentBuildContext.class, AgentBuildHookResult.class);

    private final SkillProvider skillProvider;

    public AvailableSkillsPromptHook(SkillProvider skillProvider) {
        this.skillProvider = Objects.requireNonNull(skillProvider, "skillProvider");
    }

    @Override
    public String name() {
        return REGISTRATION_NAME;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public AgentBuildHookResult apply(AgentBuildContext context) {
        PromptDefinition prompt = context.definition().definition().prompt();
        if (!prompt.systemPrompt().contains(PLACEHOLDER)) {
            return new ContinueAgentBuild(List.of());
        }

        Set<String> enabledSkills = context.definition().definition().enabledSkills();
        List<SkillMeta> skills =
                List.copyOf(
                                Objects.requireNonNull(
                                        skillProvider.loadSkills(),
                                        "SkillProvider.loadSkills 返回值不能为空"))
                        .stream()
                        .filter(skill -> enabledSkills.contains(skill.name()))
                        .toList();
        String replaced = prompt.systemPrompt().replace(PLACEHOLDER, format(skills));
        return new ContinueAgentBuild(
                List.of(new ReplacePrompt(new PromptDefinition(replaced, prompt.maxIterations()))));
    }

    private String format(List<SkillMeta> skills) {
        StringBuilder result = new StringBuilder("<available_skills>\n");
        for (SkillMeta skill : skills) {
            result.append("<skill>\n")
                    .append("<name>")
                    .append(escapeXml(skill.name()))
                    .append("</name>\n")
                    .append("<description>")
                    .append(escapeXml(skill.description()))
                    .append("</description>\n")
                    .append("</skill>\n");
        }
        return result.append("</available_skills>").toString();
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
