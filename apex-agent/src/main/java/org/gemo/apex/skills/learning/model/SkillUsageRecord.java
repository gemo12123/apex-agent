package org.gemo.apex.skills.learning.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SkillUsageRecord {
    private String id;
    private String agentKey;
    private String skillName;
    private String sessionId;
    private Integer turnNo;
    private Long activationMessageSortNo;
    private LocalDateTime createdTime;
}
