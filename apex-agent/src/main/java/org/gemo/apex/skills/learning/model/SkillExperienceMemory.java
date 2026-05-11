package org.gemo.apex.skills.learning.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SkillExperienceMemory {
    private String id;
    private String agentKey;
    private String skillName;
    private String content;
    private Long versionNo;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
