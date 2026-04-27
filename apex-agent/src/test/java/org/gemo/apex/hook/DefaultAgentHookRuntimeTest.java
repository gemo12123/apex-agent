package org.gemo.apex.hook;

import org.gemo.apex.config.model.AgentHooksConfig;
import org.gemo.apex.config.model.HookBindingConfig;
import org.gemo.apex.hook.tool.PostToolCallHook;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.gemo.apex.hook.tool.PreToolCallHook;
import org.gemo.apex.hook.tool.PreToolCallHookContext;
import org.gemo.apex.hook.tool.PreToolCallHookResult;
import org.gemo.apex.hook.tool.ToolConfirmationSpec;
import org.gemo.apex.hook.tool.builtin.PlainTextTruncateHook;
import org.gemo.apex.service.AgentWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class DefaultAgentHookRuntimeTest {

    @Mock
    private AgentWorkspaceService agentWorkspaceService;

    @Mock
    private ApplicationContext applicationContext;

    private DefaultAgentHookRuntime runtime;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        runtime = new DefaultAgentHookRuntime(agentWorkspaceService, applicationContext, new ToolMatcher());
    }

    @Test
    void runPreHooksShouldApplyMutationsInOrderAndStopOnConfirmation() {
         AgentHooksConfig config = AgentHooksConfig.builder()
                 .preToolCall(List.of(
                         HookBindingConfig.builder()
                                 .bean("mutateRoomHook")
                                 .order(10)
                                 .tools(List.of("meeting_tool"))
                                 .build(),
                         HookBindingConfig.builder()
                                 .bean("toolConfirmHook")
                                 .order(20)
                                 .tools(List.of("meeting_tool"))
                                 .build(),
                        HookBindingConfig.builder()
                                .bean("lateHook")
                                .order(30)
                                .tools(List.of("meeting_tool"))
                                 .build()))
                 .build();

        when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
        when(applicationContext.getBean("mutateRoomHook", PreToolCallHook.class))
                .thenReturn(context -> PreToolCallHookResult.proceedWithUpdatedArgs(Map.of(
                        "room", "B2001",
                        "date", "2026-04-22")));
        when(applicationContext.getBean("toolConfirmHook", PreToolCallHook.class))
                .thenReturn(context -> {
                    assertEquals("B2001", context.getArguments().get("room"));
                    return PreToolCallHookResult.requestConfirmation(ToolConfirmationSpec.builder()
                            .title("预订会议室前确认")
                            .toolDisplayName("会议室助手")
                            .build());
                });

         PreToolCallHookResult result = runtime.runPreHooks(PreToolCallHookContext.builder()
                 .agentKey("default_agent")
                 .toolName("meeting_tool")
                 .arguments(new LinkedHashMap<>(Map.of(
                         "room", "A1001",
                        "date", "2026-04-22")))
                .build());

         assertEquals(PreToolCallHookResult.Outcome.REQUEST_CONFIRMATION, result.getOutcome());
         assertEquals("会议室助手", result.getConfirmationSpec().getToolDisplayName());
         assertEquals("预订会议室前确认", result.getConfirmationSpec().getTitle());
        assertEquals(List.of("mutateRoomHook", "toolConfirmHook"), result.getExecutedHookBeans());
     }

    @Test
    void runPostHooksShouldReplacePlainTextButKeepJson() {
        AgentHooksConfig config = AgentHooksConfig.builder()
                .postToolCall(List.of(HookBindingConfig.builder()
                        .bean("plainTextTruncateHook")
                        .order(100)
                        .tools(List.of("*"))
                        .options(Map.of("max-length", 8))
                        .build()))
                .build();

        when(agentWorkspaceService.getHooks("default_agent")).thenReturn(config);
        when(applicationContext.getBean("plainTextTruncateHook", PostToolCallHook.class))
                .thenReturn(new PlainTextTruncateHook());

        PostToolCallHookResult textResult = runtime.runPostHooks(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .toolName("contacts_tool")
                .originalResult("very long plain text")
                .currentResult("very long plain text")
                .build());

        PostToolCallHookResult jsonResult = runtime.runPostHooks(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .toolName("contacts_tool")
                .originalResult("{\"ok\":true}")
                .currentResult("{\"ok\":true}")
                .build());

        assertEquals(PostToolCallHookResult.Outcome.REPLACE_RESULT, textResult.getOutcome());
        assertTrue(textResult.getNextResult().contains("[truncated by post-hook"));
        assertEquals(PostToolCallHookResult.Outcome.KEEP, jsonResult.getOutcome());
    }
}
