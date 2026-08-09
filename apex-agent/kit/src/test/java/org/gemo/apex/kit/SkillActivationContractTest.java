package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.kit.hook.SkillActivationStateHook;
import org.gemo.apex.kit.tool.ActivateSkillTool;
import org.junit.jupiter.api.Test;

class SkillActivationContractTest {
    private final SkillProvider skills =
            () -> List.of(new SkillDefinition("pdf", "PDF", "使用 PDF 指令", Map.of()));
    private final ActivateSkillTool tool = new ActivateSkillTool(skills);

    @Test
    void returnsEnabledSkillInstructionsAndSuccessMetadata() {
        ToolResult result =
                tool.execute(
                        call("pdf"),
                        KitFixtures.execution(Set.of("pdf"), Set.of()),
                        KitFixtures.OBSERVER);

        assertEquals("使用 PDF 指令", result.content());
        assertEquals("pdf", result.metadata().get(ActivateSkillTool.ACTIVATED_SKILL_METADATA));
    }

    @Test
    void rejectsInvalidUnavailableAndUnknownSkills() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                call(""),
                                KitFixtures.execution(Set.of("pdf"), Set.of()),
                                KitFixtures.OBSERVER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                call("pdf"),
                                KitFixtures.execution(Set.of(), Set.of()),
                                KitFixtures.OBSERVER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                call("unknown"),
                                KitFixtures.execution(Set.of("unknown"), Set.of()),
                                KitFixtures.OBSERVER));
    }

    @Test
    void optionalHookCreatesIdempotentDeltaOnlyForSuccessfulMetadata() {
        SkillActivationStateHook hook = new SkillActivationStateHook();
        ToolResult success =
                new ToolResult(
                        "call-1",
                        ActivateSkillTool.NAME,
                        "instructions",
                        Map.of(ActivateSkillTool.ACTIVATED_SKILL_METADATA, "pdf"));

        ContinuePostToolCall first =
                assertInstanceOf(ContinuePostToolCall.class, hook.apply(post(success)));
        ContinuePostToolCall repeated =
                assertInstanceOf(ContinuePostToolCall.class, hook.apply(post(success)));
        ContinuePostToolCall failed =
                assertInstanceOf(
                        ContinuePostToolCall.class,
                        hook.apply(
                                post(
                                        new ToolResult(
                                                "call-1",
                                                ActivateSkillTool.NAME,
                                                "工具执行失败",
                                                Map.of()))));

        assertEquals(Set.of("pdf"), first.skillActivationDelta().activate());
        assertEquals(first.skillActivationDelta(), repeated.skillActivationDelta());
        assertEquals(Set.of(), failed.skillActivationDelta().activate());
    }

    @Test
    void optionalHookRejectsMalformedSuccessMetadata() {
        SkillActivationStateHook hook = new SkillActivationStateHook();
        ToolResult malformed =
                new ToolResult(
                        "call-1",
                        ActivateSkillTool.NAME,
                        "instructions",
                        Map.of(ActivateSkillTool.ACTIVATED_SKILL_METADATA, 1));

        assertThrows(IllegalArgumentException.class, () -> hook.apply(post(malformed)));
    }

    private ToolCall call(String command) {
        return KitFixtures.call(ActivateSkillTool.NAME, Map.of("command", command));
    }

    private PostToolCallContext post(ToolResult result) {
        return new PostToolCallContext(
                "session-1",
                KitFixtures.binding(
                        SkillActivationStateHook.REGISTRATION_NAME,
                        List.of(ActivateSkillTool.NAME),
                        Map.of()),
                call("pdf"),
                result);
    }
}
