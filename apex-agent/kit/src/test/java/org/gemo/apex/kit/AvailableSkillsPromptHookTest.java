package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.AgentBuildContext;
import org.gemo.apex.common.hook.result.AgentBuildHookResult;
import org.gemo.apex.common.hook.result.ContinueAgentBuild;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.kit.hook.AvailableSkillsPromptHook;
import org.junit.jupiter.api.Test;

class AvailableSkillsPromptHookTest {
    /** Hook 暴露稳定注册名和 AGENT_BUILD 契约。 */
    @Test
    void exposesStableAgentBuildContract() {
        AvailableSkillsPromptHook hook = new AvailableSkillsPromptHook(provider(List.of()));

        assertEquals(AvailableSkillsPromptHook.REGISTRATION_NAME, hook.name());
        assertEquals(HookPoint.AGENT_BUILD, hook.descriptor().hookPoint());
        assertEquals(AgentBuildContext.class, hook.descriptor().contextType());
        assertEquals(AgentBuildHookResult.class, hook.descriptor().resultType());
    }

    /** 仅按 Registry 顺序展示当前 Agent 启用的 Skill，并转义 XML 特殊字符。 */
    @Test
    void replacesAllPlaceholdersWithEnabledEscapedSkillsInProviderOrder() {
        AvailableSkillsPromptHook hook =
                new AvailableSkillsPromptHook(
                        provider(
                                List.of(
                                        new SkillMeta("skip", "未启用"),
                                        new SkillMeta(
                                                "xml&skill", "包含 <tag>、\"quote\" 和 'apostrophe'"),
                                        new SkillMeta("pdf", "PDF 工具"))));

        PromptDefinition prompt =
                apply(hook, "开始\n{skills}\n中间\n{skills}\n结束", Set.of("pdf", "xml&skill"));

        String availableSkills =
                """
                <available_skills>
                <skill>
                <name>xml&amp;skill</name>
                <description>包含 &lt;tag&gt;、&quot;quote&quot; 和 &apos;apostrophe&apos;</description>
                </skill>
                <skill>
                <name>pdf</name>
                <description>PDF 工具</description>
                </skill>
                </available_skills>\
                """;
        assertEquals(
                "开始\n" + availableSkills + "\n中间\n" + availableSkills + "\n结束",
                prompt.systemPrompt());
        assertEquals(7, prompt.maxIterations());
    }

    /** 没有启用 Skill 时仍生成结构完整的空容器。 */
    @Test
    void rendersEmptyContainerWhenNoSkillsAreEnabled() {
        AvailableSkillsPromptHook hook =
                new AvailableSkillsPromptHook(provider(List.of(new SkillMeta("pdf", "PDF"))));

        assertEquals(
                "技能：<available_skills>\n</available_skills>",
                apply(hook, "技能：{skills}", Set.of()).systemPrompt());
    }

    /** 没有占位符时不读取 Provider，也不产生定义操作。 */
    @Test
    void leavesDefinitionUntouchedWithoutPlaceholder() {
        AtomicInteger loads = new AtomicInteger();
        AvailableSkillsPromptHook hook =
                new AvailableSkillsPromptHook(
                        new SkillProvider() {
                            @Override
                            public List<SkillMeta> loadSkills() {
                                loads.incrementAndGet();
                                return List.of();
                            }

                            @Override
                            public SkillDefinition loadSkill(String skillName) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public String loadResource(String skillName, String resourcePath) {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public String loadResource(String path) {
                                throw new UnsupportedOperationException();
                            }
                        });

        ContinueAgentBuild result = applyResult(hook, "普通系统提示", Set.of());

        assertTrue(result.operations().isEmpty());
        assertEquals(0, loads.get());
    }

    private PromptDefinition apply(
            AvailableSkillsPromptHook hook, String systemPrompt, Set<String> enabledSkills) {
        ContinueAgentBuild result = applyResult(hook, systemPrompt, enabledSkills);
        ReplacePrompt operation =
                assertInstanceOf(ReplacePrompt.class, result.operations().getFirst());
        return operation.prompt();
    }

    private ContinueAgentBuild applyResult(
            AvailableSkillsPromptHook hook, String systemPrompt, Set<String> enabledSkills) {
        AgentDefinition definition =
                new AgentDefinition(
                        DefinitionSchemaVersion.V1,
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition(systemPrompt, 7),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(Set.of(), Set.of()),
                        enabledSkills,
                        Map.of(),
                        Map.of());
        AgentBuildContext context =
                new AgentBuildContext(
                        "session-1",
                        new HookBinding(
                                "available-skills",
                                AvailableSkillsPromptHook.REGISTRATION_NAME,
                                0,
                                true,
                                List.of(),
                                Map.of()),
                        new AgentDefinitionSnapshot(definition),
                        SharedDataStores.create());
        return assertInstanceOf(ContinueAgentBuild.class, hook.apply(context));
    }

    private SkillProvider provider(List<SkillMeta> skills) {
        return new SkillProvider() {
            @Override
            public List<SkillMeta> loadSkills() {
                return skills;
            }

            @Override
            public SkillDefinition loadSkill(String skillName) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String loadResource(String skillName, String resourcePath) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String loadResource(String path) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
