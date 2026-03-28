package org.gemo.apex.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.domain.Plan;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.tool.skills.CustomSkillsTool;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SuperAgent 会话上下文对象。
 * 针对每次任务请求进行实例化并常驻内存中，保存多轮对话状态及可用工具集。
 */
@Data
public class SuperAgentContext {

    /**
     * 会话标识 / 任务 ID。
     */
    private String sessionId;

    /**
     * 当前会话绑定的 Agent 标识。
     */
    private String agentKey;

    /**
     * 当前用户标识。
     */
    private String userId;

    /**
     * 该 Agent 绑定的技能工具实例。
     */
    @JsonIgnore
    private CustomSkillsTool customSkillsTool;

    /**
     * 不参与压缩的固定消息，如系统提示词、记忆注入消息和环境信息。
     */
    @JsonIgnore
    private List<Message> fixedMessages = new ArrayList<>();

    /**
     * 当前会话最新的压缩摘要消息。
     */
    @JsonIgnore
    private Message latestCompressedMessage;

    /**
     * 未压缩的真实对话消息。
     */
    @JsonIgnore
    private List<Message> dialogueMessages = new ArrayList<>();

    private Long turnStartSortNo = 0L;

    private Integer persistedDialogueMessageIndex = 0;

    /**
     * 当前实例可用的工具集合。
     */
    @JsonIgnore
    private List<ToolCallback> availableTools = new ArrayList<>();

    /**
     * 当前运行阶段。
     */
    private Stage currentStage = Stage.THINKING;

    /**
     * 当前执行模式。
     */
    private ModeEnum executionMode;

    /**
     * 当前执行状态。
     */
    private ExecutionStatus executionStatus = ExecutionStatus.IN_PROGRESS;

    /**
     * 前端用户返回的工具结果。
     */
    @JsonIgnore
    private Map<String, Object> pendingToolResult;

    /**
     * 当前任务的计划对象。
     */
    @JsonIgnore
    private Plan plan;

    /**
     * 当前正在执行的阶段 ID。
     */
    private String currentStageId;

    /**
     * SSE 发送器。
     */
    @JsonIgnore
    private SseEmitter sseEmitter;

    /**
     * 最新摘要覆盖到的排序号。
     */
    private Long latestCompressedSortNo = 0L;

    /**
     * 下一条消息的排序号。
     */
    private Long nextMessageSortNo = 1L;

    /**
     * 当前轮次召回到的记忆集合。
     */
    @JsonIgnore
    private MemoryRecallPackage memoryRecallPackage = new MemoryRecallPackage();

    /**
     * 当前会话轮次号。
     */
    private Integer turnNo = 0;

    /**
     * 最近活跃时间。
     */
    private LocalDateTime lastActiveTime = LocalDateTime.now();

    /**
     * 兼容旧代码的消息访问方法，返回未压缩对话消息。
     */
    public List<Message> getMessages() {
        return dialogueMessages;
    }

    /**
     * 兼容旧代码的消息设置方法。
     */
    public void setMessages(List<Message> messages) {
        this.dialogueMessages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }

    /**
     * 追加一条对话消息，并更新最近活跃时间。
     */
    public void addMessage(Message message) {
        if (message != null) {
            this.dialogueMessages.add(message);
            this.lastActiveTime = LocalDateTime.now();
        }
    }

    /**
     * 内部阶段枚举。
     */
    public enum Stage {
        THINKING,
        MODE_CONFIRMATION,
        EXECUTION
    }
}
