package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户执行历史记忆实体。
 */
@Data
@TableName("user_execution_history_memory")
public class UserExecutionHistoryMemoryEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("agent_key")
    private String agentKey;

    @TableField("topic_key")
    private String topicKey;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("time_scope")
    private String timeScope;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    @TableField("structured_payload")
    private String structuredPayload;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("last_turn_no")
    private Integer lastTurnNo;

    @TableField("version_no")
    private Long versionNo;

    @TableField("status")
    private String status;

    @TableField("source_session_id")
    private String sourceSessionId;

    @TableField("observed_time")
    private LocalDateTime observedTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
