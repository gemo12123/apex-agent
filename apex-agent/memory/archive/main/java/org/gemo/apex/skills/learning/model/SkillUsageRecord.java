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
    private Long turnNo;
    private Long activationMessageSortNo;
    private LocalDateTime createdTime;

    public static class SkillUsageRecordBuilder {
        public SkillUsageRecordBuilder turnNo(Long turnNo) {
            this.turnNo = turnNo;
            return this;
        }

        public SkillUsageRecordBuilder turnNo(Integer turnNo) {
            this.turnNo = turnNo != null ? turnNo.longValue() : null;
            return this;
        }
    }
}
