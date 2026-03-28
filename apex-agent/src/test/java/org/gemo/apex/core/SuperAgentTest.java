package org.gemo.apex.core;

import org.gemo.apex.component.interceptor.ToolInterceptor;
import org.gemo.apex.component.tool.GlobalToolRegistry;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.conversation.ConversationMemoryManager;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.recall.MemoryRecallService;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.memory.write.MemoryLifecycleManager;
import org.gemo.apex.service.AgentWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SuperAgentTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolInterceptor toolInterceptor;

    @Mock
    private ToolCallingManager toolCallingManager;

    @Mock
    private AgentWorkspaceService agentWorkspaceService;

    @Mock
    private ConversationMemoryManager conversationMemoryManager;

    @Mock
    private MemoryRecallService memoryRecallService;

    @Mock
    private SessionContextStore sessionContextStore;

    @Mock
    private MemoryLifecycleManager memoryLifecycleManager;

    @Mock
    private GlobalToolRegistry globalToolRegistry;

    @InjectMocks
    private SuperAgent superAgent;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(conversationMemoryManager.buildModelMessages(any(SuperAgentContext.class)))
                .thenReturn(List.of(new UserMessage("hello")));
        when(memoryRecallService.recall(any(SuperAgentContext.class))).thenReturn(new MemoryRecallPackage());
        when(agentWorkspaceService.getThinkingPrompt(anyString())).thenReturn("{skills}\n{available_tools_desc}\n{date}");
        when(agentWorkspaceService.getModeConfirmationPrompt(anyString()))
                .thenReturn("{skills}\n{available_tools_desc}\n{date}");
        when(agentWorkspaceService.getReActPrompt(anyString())).thenReturn("{skills}\n{available_tools_desc}\n{date}");
        when(agentWorkspaceService.getPlanExecutorWritePlanPrompt(anyString()))
                .thenReturn("{skills}\n{available_tools_desc}\n{date}");
        when(agentWorkspaceService.getPlanExecutorRunPrompt(anyString()))
                .thenReturn("{skills}\n{available_tools_desc}\n{date}");
        when(agentWorkspaceService.getAgentRules(anyString())).thenReturn("");
    }

    @Test
    public void testThinkingStage_EndsWithoutTools() {
        SuperAgentContext context = new SuperAgentContext();
        context.setAgentKey("test-agent");
        context.setCurrentStage(SuperAgentContext.Stage.THINKING);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        AssistantMessage msg = new AssistantMessage("I have finished thinking.");
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        superAgent.execute(context);

        assertEquals(SuperAgentContext.Stage.MODE_CONFIRMATION, context.getCurrentStage());
        verify(chatModel, times(2)).stream(any(Prompt.class));
        verify(sessionContextStore, times(1)).save(context);
    }

    @Test
    public void testExecutionStage_EndsWithoutTools() {
        SuperAgentContext context = new SuperAgentContext();
        context.setAgentKey("test-agent");
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.REACT);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);

        AssistantMessage msg = new AssistantMessage("Task completely finished.");
        ChatResponse response = new ChatResponse(List.of(new Generation(msg)));

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        superAgent.execute(context);

        assertEquals(SuperAgentContext.Stage.EXECUTION, context.getCurrentStage());
        verify(chatModel, times(1)).stream(any(Prompt.class));
        verify(sessionContextStore, times(1)).save(context);
    }
}
