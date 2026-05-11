package org.gemo.apex.skills.learning.model;

public record SkillUsageValidationResult(
        SkillUsageRecord record,
        boolean isValid,
        String reason) {

    public static SkillUsageValidationResult valid(SkillUsageRecord record) {
        return new SkillUsageValidationResult(record, true, "");
    }

    public static SkillUsageValidationResult invalid(SkillUsageRecord record, String reason) {
        return new SkillUsageValidationResult(record, false, reason);
    }
}
