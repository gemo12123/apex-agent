package org.gemo.apex.skills.learning.model;

import java.util.List;

public record SkillConversationSlice(
        String sessionId,
        Long activationMessageSortNo,
        List<SkillSessionMessage> messages) {

    public String getSessionId() {
        return sessionId;
    }

    public Long getActivationMessageSortNo() {
        return activationMessageSortNo;
    }

    public List<SkillSessionMessage> getMessages() {
        return messages;
    }
}
