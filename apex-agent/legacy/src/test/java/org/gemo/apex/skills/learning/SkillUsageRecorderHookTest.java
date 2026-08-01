package org.gemo.apex.skills.learning;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.hook.tool.PostToolCallHookContext;
import org.gemo.apex.hook.tool.PostToolCallHookResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SkillUsageRecorderHookTest {

    @Test
    void usageHookShouldInsertOnlyWhenToolExecutionSucceeded() {
        SkillUsageRecordRepository repository = mock(SkillUsageRecordRepository.class);
        SkillUsageRecorderHook hook = new SkillUsageRecorderHook(new SkillExperienceLearningProperties(), repository);

        PostToolCallHookResult failedResult = hook.apply(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .sessionId("session-1")
                .toolName("activate_skill")
                .arguments(Map.of("command", "writing-plans"))
                .toolExecutionSucceeded(false)
                .superAgentContext(context())
                .build());

        assertEquals(PostToolCallHookResult.Outcome.KEEP, failedResult.getOutcome());
        verify(repository, never()).insert(any());

        PostToolCallHookResult successResult = hook.apply(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .sessionId("session-1")
                .toolName("activate_skill")
                .arguments(Map.of("command", "writing-plans"))
                .toolExecutionSucceeded(true)
                .superAgentContext(context())
                .build());

        assertEquals(PostToolCallHookResult.Outcome.KEEP, successResult.getOutcome());
        verify(repository).insert(any());
    }

    @Test
    void usageHookShouldSkipWhenLearningDisabled() {
        SkillExperienceLearningProperties properties = new SkillExperienceLearningProperties();
        properties.setEnabled(false);
        SkillUsageRecordRepository repository = mock(SkillUsageRecordRepository.class);
        SkillUsageRecorderHook hook = new SkillUsageRecorderHook(properties, repository);

        PostToolCallHookResult result = hook.apply(PostToolCallHookContext.builder()
                .agentKey("default_agent")
                .sessionId("session-1")
                .toolName("activate_skill")
                .arguments(Map.of("command", "writing-plans"))
                .toolExecutionSucceeded(true)
                .superAgentContext(context())
                .build());

        assertEquals(PostToolCallHookResult.Outcome.KEEP, result.getOutcome());
        verify(repository, never()).insert(any());
    }

    private SuperAgentContext context() {
        SuperAgentContext context = new SuperAgentContext();
        context.setTurnNo(3);
        context.setNextMessageSortNo(12L);
        return context;
    }
}
