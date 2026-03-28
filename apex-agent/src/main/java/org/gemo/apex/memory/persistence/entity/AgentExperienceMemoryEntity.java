package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 智能体经验记忆实体。
 */
@Data
@TableName("agent_experience_memory")
public class AgentExperienceMemoryEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("agent_key")
    private String agentKey;

    @TableField("topic_key")
    private String topicKey;

    @TableField("memory_key")
    private String memoryKey;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("structured_payload")
    private String structuredPayload;

    @TableField("confidence")
    private BigDecimal confidence;

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
