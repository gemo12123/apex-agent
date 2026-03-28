package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话元数据实体。
 */
@Data
@TableName("agent_session")
public class AgentSessionEntity {

    @TableId(value = "session_id", type = IdType.INPUT)
    private String sessionId;

    @TableField("user_id")
    private String userId;

    @TableField("agent_key")
    private String agentKey;

    @TableField("execution_status")
    private String executionStatus;

    @TableField("current_stage")
    private String currentStage;

    @TableField("execution_mode")
    private String executionMode;

    @TableField("last_active_time")
    private LocalDateTime lastActiveTime;

    /**
     * 运行态快照 JSON。
     */
    @TableField("runtime_snapshot")
    private String runtimeSnapshot;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
