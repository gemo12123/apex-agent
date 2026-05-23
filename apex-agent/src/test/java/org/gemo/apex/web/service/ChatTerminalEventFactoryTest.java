package org.gemo.apex.web.service;

import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.message.EndMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatTerminalEventFactoryTest {

    private final ChatTerminalEventFactory factory = new ChatTerminalEventFactory();

    @Test
    void buildFailureEndMessageShouldExposeExecutionStatusAndErrorMetadata() {
        SuperAgentContext context = new SuperAgentContext();
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.FAILED);

        EndMessage endMessage = factory.buildForFailure(context, "STREAM_EXECUTION_FAILED",
                "Model returned neither content nor tool calls");

        assertEquals("react", endMessage.getContext().get("mode"));
        assertEquals("FAILED", endMessage.getContext().get("execution_status"));
        assertEquals("STREAM_EXECUTION_FAILED", endMessage.getContext().get("error_code"));
        assertEquals("Model returned neither content nor tool calls", endMessage.getContext().get("error_message"));
    }

    @Test
    void buildRequestFailureEndMessageShouldNotRequireSessionContext() {
        EndMessage endMessage = factory.buildForRequestFailure("STREAM_CONTEXT_INIT_FAILED",
                "Session session-1 is not resumable");

        assertEquals("FAILED", endMessage.getContext().get("execution_status"));
        assertEquals("STREAM_CONTEXT_INIT_FAILED", endMessage.getContext().get("error_code"));
        assertEquals("Session session-1 is not resumable", endMessage.getContext().get("error_message"));
    }
}
