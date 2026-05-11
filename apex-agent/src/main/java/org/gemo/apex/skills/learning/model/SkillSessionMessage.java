package org.gemo.apex.skills.learning.model;

public record SkillSessionMessage(
        Long sortNo,
        String role,
        String toolName,
        String messagePayload,
        String content) {

    public Long getSortNo() {
        return sortNo;
    }

    public String getRole() {
        return role;
    }

    public String getToolName() {
        return toolName;
    }

    public String getMessagePayload() {
        return messagePayload;
    }

    public String getContent() {
        return content;
    }
}
