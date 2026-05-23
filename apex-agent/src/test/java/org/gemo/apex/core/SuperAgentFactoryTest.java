package org.gemo.apex.core;

import org.gemo.apex.context.SuperAgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAgentFactoryTest {

    @Mock
    private SuperAgentSessionService sessionService;

    @Mock
    private SuperAgentExecutor executor;

    @InjectMocks
    private SuperAgentFactory superAgentFactory;

    @Test
    void createContextShouldDelegateToSessionService() {
        SuperAgentContext context = new SuperAgentContext();
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);

        SuperAgentContext actual = superAgentFactory.createContext("session-1", "agent-1", "hello");

        assertSame(context, actual);
    }

    @Test
    void resumeContextShouldDelegateToSessionService() {
        SuperAgentContext context = new SuperAgentContext();
        when(sessionService.resumeContext("session-1", "agent-1", Map.of("k", "v"))).thenReturn(context);

        SuperAgentContext actual = superAgentFactory.resumeContext("session-1", "agent-1", Map.of("k", "v"));

        assertSame(context, actual);
    }

    @Test
    void executeContextShouldDelegateToExecutor() {
        SuperAgentContext context = new SuperAgentContext();

        superAgentFactory.executeContext(context);

        verify(executor).execute(context);
    }
}
