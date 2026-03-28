package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 原始对话消息实体。
 */
@Data
@TableName("agent_session_dialogue_message")
public class AgentSessionDialogueMessageEntity {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("turn_no")
    private Integer turnNo;

    @TableField("sort_no")
    private Long sortNo;

    @TableField("role")
    private String role;

    @TableField("message_type")
    private String messageType;

    @TableField("content")
    private String content;

    @TableField("tool_name")
    private String toolName;

    @TableField("tool_call_id")
    private String toolCallId;

    @TableField("token_count")
    private Integer tokenCount;

    /**
     * 完整消息 JSON。
     */
    @TableField("message_payload")
    private String messagePayload;

    @TableField("compacted")
    private Boolean compacted;

    @TableField("create_time")
    private LocalDateTime createTime;
}
