package org.gemo.apex.skills.learning.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill_experience_memory")
public class SkillExperienceMemoryEntity {

    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    @TableField("agent_key")
    private String agentKey;

    @TableField("skill_name")
    private String skillName;

    @TableField("content")
    private String content;

    @TableField("version_no")
    private Long versionNo;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
