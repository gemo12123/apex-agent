package org.gemo.apex.skills.learning.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill_usage_record")
public class SkillUsageRecordEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("agent_key")
    private String agentKey;

    @TableField("skill_name")
    private String skillName;

    @TableField("session_id")
    private String sessionId;

    @TableField("turn_no")
    private Long turnNo;

    @TableField("activation_message_sort_no")
    private Long activationMessageSortNo;

    @TableField("created_time")
    private LocalDateTime createdTime;
}
