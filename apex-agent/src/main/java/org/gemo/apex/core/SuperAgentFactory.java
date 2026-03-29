package org.gemo.apex.core;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.component.tool.BuiltInToolProvider;
import org.gemo.apex.component.tool.GlobalToolRegistry;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.context.UserContextHolder;
import org.gemo.apex.memory.recall.MemoryRecallService;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.service.AgentWorkspaceService;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * SuperAgent 实例工厂。
 */
@Slf4j
@Service
public class SuperAgentFactory {

    @Autowired
    private GlobalToolRegistry globalToolRegistry;

    @Autowired
    private BuiltInToolProvider builtInToolProvider;

    @Autowired
    private AgentWorkspaceService agentWorkspaceService;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private SessionContextStore sessionContextStore;

    @Autowired
    private MemoryRecallService memoryRecallService;

    @Transactional
    public SuperAgentContext createContext(String sessionId, String agentKey, String userQuery) {
        String userId = UserContextHolder.getUserId();
        return sessionContextStore.load(sessionId)
                .map(loaded -> {
                    log.info("基于已有会话开启新轮次: sessionId={}, agentKey={}", sessionId, agentKey);
                    return createNextTurnContext(loaded, sessionId, agentKey, userId, userQuery);
                })
                .orElseGet(() -> createNewSessionContext(sessionId, agentKey, userId, userQuery));
    }

    private SuperAgentContext createNewSessionContext(String sessionId, String agentKey, String userId, String userQuery) {
        log.info("创建全新会话上下文: sessionId={}, agentKey={}", sessionId, agentKey);
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId(sessionId);
        context.setTurnNo(0);
        return initializeNewTurn(context, sessionId, agentKey, userId, userQuery);
    }

    private SuperAgentContext createNextTurnContext(SuperAgentContext context, String sessionId, String agentKey,
            String userId, String userQuery) {
        validateExistingSessionForNewTurn(context, sessionId, agentKey, userId);
        return initializeNewTurn(context, sessionId, agentKey, userId, userQuery);
    }

    private void validateExistingSessionForNewTurn(SuperAgentContext context, String sessionId, String agentKey,
            String userId) {
        if (context.getExecutionStatus() == ExecutionStatus.HUMAN_IN_THE_LOOP) {
            String message = "Session " + sessionId
                    + " is waiting for HUMAN_RESPONSE; RequestType.NEW is not allowed";
            log.warn(message);
            throw new IllegalStateException(message);
        }
        if (!Objects.equals(context.getUserId(), userId)) {
            String message = "Session " + sessionId + " belongs to another user";
            log.warn("{}, requestedUserId={}, sessionUserId={}", message, userId, context.getUserId());
            throw new IllegalStateException(message);
        }
        if (!Objects.equals(context.getAgentKey(), agentKey)) {
            String message = "Session " + sessionId + " belongs to another agent";
            log.warn("{}, requestedAgentKey={}, sessionAgentKey={}", message, agentKey, context.getAgentKey());
            throw new IllegalStateException(message);
        }
    }

    private SuperAgentContext initializeNewTurn(SuperAgentContext context, String sessionId, String agentKey,
            String userId, String userQuery) {
        context.setSessionId(sessionId);
        context.setAgentKey(agentKey);
        context.setUserId(userId);
        context.setLastActiveTime(LocalDateTime.now());
        context.setPlan(null);
        context.setCurrentStageId(null);
        context.setExecutionStatus(ExecutionStatus.IN_PROGRESS);
        context.setPendingToolResult(null);
        context.setTurnNo(context.getTurnNo() != null ? context.getTurnNo() + 1 : 1);
        context.setTurnStartSortNo(context.getLatestCompressedSortNo());
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());

        prepareRuntimeContext(context, agentKey);
        applyDefaultStageConfig(context, agentKey);

        UserMessage userMessage = new UserMessage(userQuery);
        context.addMessage(userMessage);
        context.setMemoryRecallPackage(memoryRecallService.recall(context));
        persistNewTurn(context, userMessage);
        return context;
    }

    private void persistNewTurn(SuperAgentContext context, UserMessage userMessage) {
        long baseSortNo = context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex();
        sessionContextStore.appendDialogueMessages(context.getSessionId(), context.getTurnNo(), baseSortNo,
                List.of(userMessage));
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        sessionContextStore.save(context);
    }

    public SuperAgentContext resumeContext(String sessionId, Map<String, Object> humanResponse) {
        SuperAgentContext context = sessionContextStore.load(sessionId).orElse(null);
        if (context == null || context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
            log.warn("未找到可恢复的挂起会话, sessionId={}", sessionId);
            return null;
        }
        prepareRuntimeContext(context, context.getAgentKey());
        context.setUserId(UserContextHolder.getUserId());
        context.setPendingToolResult(humanResponse != null && !humanResponse.isEmpty() ? humanResponse : new HashMap<>());
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        context.setMemoryRecallPackage(memoryRecallService.recall(context));
        return context;
    }

    public void executeContext(SuperAgentContext context) {
        SuperAgent agentEngine = applicationContext.getBean(SuperAgent.class);
        agentEngine.execute(context);
    }

    private void prepareRuntimeContext(SuperAgentContext context, String agentKey) {
        context.setAvailableTools(new java.util.ArrayList<>());
        context.setCustomSkillsTool(null);

        List<ToolCallback> builtInTools = builtInToolProvider.getBuiltInTools();
        context.getAvailableTools().addAll(builtInTools);

        List<ToolCallback> mcpTools = globalToolRegistry.getMcpToolCallbacks(agentKey);
        context.getAvailableTools().addAll(mcpTools);

        List<ToolCallback> subAgentTools = globalToolRegistry.getSubAgentToolCallbacks(agentKey);
        context.getAvailableTools().addAll(subAgentTools);

        CustomSkillsTool customSkillsTool = globalToolRegistry.getSkillsTool(agentKey);
        if (customSkillsTool != null) {
            context.getAvailableTools().add(customSkillsTool.getToolCallback());
            context.setCustomSkillsTool(customSkillsTool);
        }
    }

    private void applyDefaultStageConfig(SuperAgentContext context, String agentKey) {
        ModeEnum executionMode = agentWorkspaceService.getDefaultExecutionMode(agentKey);

        if (executionMode == null) {
            throw new IllegalStateException(
                    "Agent " + agentKey + " is missing required default execution mode");
        }

        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(executionMode);
    }
}
