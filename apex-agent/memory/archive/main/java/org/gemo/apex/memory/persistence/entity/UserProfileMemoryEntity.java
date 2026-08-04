package org.gemo.apex.memory.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户画像记忆实体。
 */
@Data
@TableName("user_profile_memory")
public class UserProfileMemoryEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("user_id")
    private String userId;

    @TableField("agent_key")
    private String agentKey;

    @TableField("memory_key")
    private String memoryKey;

    @TableField("memory_category")
    private String memoryCategory;

    @TableField("title")
    private String title;

    @TableField("content")
    private String content;

    @TableField("structured_payload")
    private String structuredPayload;

    @TableField("confidence")
    private BigDecimal confidence;

    @TableField("importance")
    private Integer importance;

    @TableField("status")
    private String status;

    @TableField("source_session_id")
    private String sourceSessionId;

    @TableField("observed_time")
    private LocalDateTime observedTime;

    @TableField("expire_time")
    private LocalDateTime expireTime;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
