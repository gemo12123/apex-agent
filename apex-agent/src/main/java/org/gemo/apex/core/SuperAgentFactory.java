package org.gemo.apex.core;

import org.gemo.apex.component.interceptor.ToolInterceptor;
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
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@Service
public class SuperAgentFactory {

    private final SuperAgentSessionService sessionService;
    private final HumanInLoopResumer humanInLoopResumer;
    private final StageToolResolver stageToolResolver;
    private final AgentPromptAssembler agentPromptAssembler;
    private final ModelResponseStreamer modelResponseStreamer;
    private final ToolInterceptor toolInterceptor;
    private final ToolCallProcessor toolCallProcessor;
    private final ConversationMemoryManager conversationMemoryManager;
    private final SessionContextStore sessionContextStore;
    private final MemoryLifecycleManager memoryLifecycleManager;

    public SuperAgentFactory(SuperAgentSessionService sessionService,
            HumanInLoopResumer humanInLoopResumer,
            StageToolResolver stageToolResolver,
            AgentPromptAssembler agentPromptAssembler,
            ModelResponseStreamer modelResponseStreamer,
            ToolInterceptor toolInterceptor,
            ToolCallProcessor toolCallProcessor,
            ConversationMemoryManager conversationMemoryManager,
            SessionContextStore sessionContextStore,
            MemoryLifecycleManager memoryLifecycleManager) {
        this.sessionService = sessionService;
        this.humanInLoopResumer = humanInLoopResumer;
        this.stageToolResolver = stageToolResolver;
        this.agentPromptAssembler = agentPromptAssembler;
        this.modelResponseStreamer = modelResponseStreamer;
        this.toolInterceptor = toolInterceptor;
        this.toolCallProcessor = toolCallProcessor;
        this.conversationMemoryManager = conversationMemoryManager;
        this.sessionContextStore = sessionContextStore;
        this.memoryLifecycleManager = memoryLifecycleManager;
    }

    public SuperAgent create(ChatRequest request, SseEmitter emitter) {
        return request.getType() == org.gemo.apex.constant.RequestType.HUMAN_RESPONSE
                ? createForResume(request, emitter)
                : createForNew(request, emitter);
    }

    public SuperAgentContext createContext(String sessionId, String agentKey, String userQuery) {
        return sessionService.createContext(sessionId, agentKey, userQuery);
    }

    public SuperAgentContext resumeContext(String sessionId, String agentKey, Map<String, Object> humanResponse) {
        return sessionService.resumeContext(sessionId, agentKey, humanResponse);
    }

    public void executeContext(SuperAgentContext context) {
        newAgent(context).run();
    }

    private SuperAgent createForNew(ChatRequest request, SseEmitter emitter) {
        SuperAgentContext context = createContext(request.getSessionId(), request.getAgentKey(), request.getQuery());
        context.setSseEmitter(emitter);
        return newAgent(context);
    }

    private SuperAgent createForResume(ChatRequest request, SseEmitter emitter) {
        SuperAgentContext context = resumeContext(
                request.getSessionId(), request.getAgentKey(), request.getHumanResponse());
        context.setSseEmitter(emitter);
        return newAgent(context);
    }

    private SuperAgent newAgent(SuperAgentContext context) {
        return new SuperAgent(
                context,
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
}
