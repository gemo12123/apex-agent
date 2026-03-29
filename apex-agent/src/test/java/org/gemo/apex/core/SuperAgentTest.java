package org.gemo.apex.core;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.ExecutionFinalizer;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.SuperAgentLoopRunner;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class SuperAgentTest {

    @Mock
    private HumanInLoopResumer humanInLoopResumer;

    @Mock
    private SuperAgentLoopRunner superAgentLoopRunner;

    @Mock
    private ExecutionFinalizer executionFinalizer;

    private SuperAgent superAgent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        superAgent = new SuperAgent(humanInLoopResumer, superAgentLoopRunner, executionFinalizer);
    }

    @Test
    void executeShouldDelegateAndFinalizeOnSuccess() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        superAgent.execute(context);

        verify(humanInLoopResumer).resume(context);
        verify(superAgentLoopRunner).run(context);
        verify(executionFinalizer).finalizeTurn(context);
    }

    @Test
    void executeShouldFinalizeOnceAndRethrowWhenHumanInLoop() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        doAnswer(invocation -> {
            context.setExecutionStatus(ExecutionStatus.HUMAN_IN_THE_LOOP);
            throw new HumanInTheLoopException("waiting");
        }).when(superAgentLoopRunner).run(context);

        assertThrows(HumanInTheLoopException.class, () -> superAgent.execute(context));

        verify(humanInLoopResumer).resume(context);
        verify(executionFinalizer, times(1)).finalizeTurn(context);
    }

    @Test
    void executeShouldMarkFailedAndFinalizeOnRuntimeException() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        doThrow(new IllegalStateException("boom")).when(superAgentLoopRunner).run(context);

        assertThrows(IllegalStateException.class, () -> superAgent.execute(context));

        assertEquals(ExecutionStatus.FAILED, context.getExecutionStatus());
        verify(executionFinalizer).finalizeTurn(context);
    }
}
