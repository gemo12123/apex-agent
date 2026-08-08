package org.gemo.apex.platform.web;

import static org.junit.jupiter.api.Assertions.*;

import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.platform.PlatformFixtures;
import org.junit.jupiter.api.Test;

class SessionStateViewMapperTest {
    /** 挂起问题应映射为既有AskHuman协议且不生成新标识 */
    @Test
    void mapsSuspendedQuestionToExistingAskHumanProtocolWithoutNewIdentifier() {
        var view = new SessionStateViewMapper().map(PlatformFixtures.suspendedSnapshot());
        String json = JsonUtils.toJson(view.pendingInteraction());
        assertEquals("HUMAN_IN_THE_LOOP", view.executionStatus());
        assertTrue(json.contains("\"event_type\":\"HUMAN_INTERVENTION\""));
        assertTrue(json.contains("\"interaction_type\":\"ASK_HUMAN\""));
        assertTrue(json.contains("\"tool_call_id\":\"call-1\""));
        assertTrue(json.contains("\"invocation_id\":\"invocation-1\""));
    }

    /** 挂起确认应映射为既有ToolConfirmation协议 */
    @Test
    void mapsSuspendedConfirmationToExistingToolConfirmationProtocol() {
        var view = new SessionStateViewMapper().map(PlatformFixtures.confirmationSnapshot());
        String json = JsonUtils.toJson(view.pendingInteraction());
        assertTrue(json.contains("\"event_type\":\"HUMAN_INTERVENTION\""));
        assertTrue(json.contains("\"interaction_type\":\"TOOL_CONFIRMATION\""));
        assertTrue(json.contains("\"confirmation_id\":\"confirmation-1\""));
        assertTrue(json.contains("\"tool_call_id\":\"call-1\""));
    }
}
