package org.gemo.apex.core;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.component.tool.BuiltInToolProvider;
import org.gemo.apex.component.tool.GlobalToolRegistry;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.definition.agent.AgentDefinition;
import org.gemo.apex.definition.agent.IAgentDefinitionLoader;
import org.gemo.apex.exception.SessionResumeNotAllowedException;
import org.gemo.apex.memory.context.UserContextHolder;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.hook.lifecycle.AgentExecutionStore;
import org.gemo.apex.hook.lifecycle.InMemoryAgentExecutionStore;
import org.gemo.apex.skills.Skills;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class SuperAgentSessionService {

    @Autowired
    private GlobalToolRegistry globalToolRegistry;

    @Autowired
    private BuiltInToolProvider builtInToolProvider;

    @Autowired
    private IAgentDefinitionLoader agentDefinitionLoader;

    @Autowired
    private SessionContextStore sessionContextStore;

    @Autowired(required = false)
    private AgentExecutionStore agentExecutionStore = new InMemoryAgentExecutionStore();

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

    public SuperAgentContext resumeContext(String sessionId, String agentKey, Map<String, Object> humanResponse) {
        SuperAgentContext context = sessionContextStore.load(sessionId)
                .orElseThrow(() -> new SessionResumeNotAllowedException("Session " + sessionId + " is not resumable"));

        if (context.getExecutionStatus() != ExecutionStatus.HUMAN_IN_THE_LOOP) {
            throw new SessionResumeNotAllowedException("Session " + sessionId + " is not resumable");
        }

        String userId = UserContextHolder.getUserId();
        if (!Objects.equals(context.getUserId(), userId)) {
            String message = "Session " + sessionId + " belongs to another user";
            log.warn("{}, requestedUserId={}, sessionUserId={}", message, userId, context.getUserId());
            throw new SessionResumeNotAllowedException(message);
        }
        if (!Objects.equals(context.getAgentKey(), agentKey)) {
            String message = "Session " + sessionId + " belongs to another agent";
            log.warn("{}, requestedAgentKey={}, sessionAgentKey={}", message, agentKey, context.getAgentKey());
            throw new SessionResumeNotAllowedException(message);
        }

        prepareRuntimeContext(context, context.getAgentKey());
        context.setUserId(userId);
        context.setPendingToolResult(humanResponse != null && !humanResponse.isEmpty() ? humanResponse : new HashMap<>());
        context.setNextMessageSortNo(context.getTurnStartSortNo() + context.getPersistedDialogueMessageIndex() + 1L);
        return context;
    }

    private SuperAgentContext createNewSessionContext(String sessionId, String agentKey, String userId, String userQuery) {
        log.info("创建全新会话上下文: sessionId={}, agentKey={}", sessionId, agentKey);
        SuperAgentContext context = new SuperAgentContext();
        context.setSessionId(sessionId);
        context.setTurnNo(0L);
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
        context.setTurnNo(agentExecutionStore.nextTurnNo());
        context.setIterationNo(0);
        context.setActiveSkillNames(new java.util.ArrayList<>());
        context.setEnabledToolNames(new java.util.ArrayList<>());
        context.setWorkingMessages(new java.util.ArrayList<>());
        context.setTurnStartSortNo(context.getLatestCompressedSortNo());
        context.setPersistedDialogueMessageIndex(context.getDialogueMessages().size());

        prepareRuntimeContext(context, agentKey);

        UserMessage userMessage = new UserMessage(userQuery);
        context.addMessage(userMessage);
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

    private void prepareRuntimeContext(SuperAgentContext context, String agentKey) {
        AgentDefinition definition = agentDefinitionLoader.load(agentKey);
        context.setAvailableTools(new java.util.ArrayList<>());
        context.setSkills(null);

        List<ToolCallback> builtInTools = builtInToolProvider.getBuiltInTools();
        context.getAvailableTools().addAll(builtInTools);

        List<ToolCallback> mcpTools = globalToolRegistry.getMcpToolCallbacks(agentKey);
        context.getAvailableTools().addAll(mcpTools);

        List<ToolCallback> subAgentTools = globalToolRegistry.getSubAgentToolCallbacks(agentKey);
        context.getAvailableTools().addAll(subAgentTools);

        Skills skills = globalToolRegistry.getSkillsTool(agentKey);
        if (skills != null) {
            context.getAvailableTools().addAll(Arrays.asList(skills.toolCallbacks()));
            context.setSkills(skills);
        }

        if (definition.defaultExecutionMode() == null) {
            throw new IllegalStateException(
                    "Agent " + agentKey + " is missing required default execution mode");
        }

        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(definition.defaultExecutionMode());
    }
}
