package org.gemo.apex.core;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.constant.RequestType;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.AgentPromptAssembler;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.ModelResponseStreamer;
import org.gemo.apex.core.engine.StageToolResolver;
import org.gemo.apex.core.engine.ToolCallProcessor;
import org.gemo.apex.domain.dto.ChatRequest;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAgentFactoryTest {

    @Mock
    private SuperAgentSessionService sessionService;

    @Mock
    private HumanInLoopResumer humanInLoopResumer;

    @Mock
    private StageToolResolver stageToolResolver;

    @Mock
    private AgentPromptAssembler agentPromptAssembler;

    @Mock
    private ModelResponseStreamer modelResponseStreamer;

    @Mock
    private ToolInterceptor toolInterceptor;

    @Mock
    private ToolCallProcessor toolCallProcessor;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private SessionContextStore sessionContextStore;

    @Mock
    private MemoryLifecycleManager memoryLifecycleManager;

    private SuperAgentFactory superAgentFactory;

    @BeforeEach
    void setUp() {
        superAgentFactory = new SuperAgentFactory(
                sessionService,
                humanInLoopResumer,
                stageToolResolver,
                agentPromptAssembler,
                modelResponseStreamer,
                toolInterceptor,
                toolCallProcessor,
                conversationMemoryManager,
                sessionContextStore,
                memoryLifecycleManager);
    }

    @Test
    void createShouldBuildAgentForNewRequest() {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setQuery("hello");
        SseEmitter emitter = new SseEmitter(1000L);

        SuperAgentContext context = new SuperAgentContext();
        when(sessionService.createContext("session-1", "agent-1", "hello")).thenReturn(context);

        SuperAgent agent = superAgentFactory.create(request, emitter);

        assertSame(context, agent.getContext());
        assertSame(emitter, context.getSseEmitter());
    }

    @Test
    void createShouldBuildAgentForHumanResponse() {
        ChatRequest request = new ChatRequest();
        request.setSessionId("session-1");
        request.setAgentKey("agent-1");
        request.setType(RequestType.HUMAN_RESPONSE);
        request.setHumanResponse(Map.of("approved", true));
        SseEmitter emitter = new SseEmitter(1000L);

        SuperAgentContext context = new SuperAgentContext();
        when(sessionService.resumeContext("session-1", "agent-1", Map.of("approved", true))).thenReturn(context);

        SuperAgent agent = superAgentFactory.create(request, emitter);

        assertSame(context, agent.getContext());
        assertSame(emitter, context.getSseEmitter());
    }
}
