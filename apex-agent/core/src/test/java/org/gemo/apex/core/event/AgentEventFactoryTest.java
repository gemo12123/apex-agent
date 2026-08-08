package org.gemo.apex.core.event;

import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.protocol.event.EndMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentEventFactoryTest {
    private final AgentEventFactory factory = new AgentEventFactory();

    /**
     * streamContent保持react与contentId且不产生stageId
     */
    @Test
    void streamContentPreservesReactAndContentIdWithoutStageId() {
        var tree = JsonUtils.toTree(factory.streamContent("content-1", "hello"));
        assertEquals("STREAM_CONTENT", tree.get("event_type").asText());
        assertEquals("react", tree.at("/context/mode").asText());
        assertEquals("content-1", tree.at("/context/content_id").asText());
        assertFalse(tree.get("context").has("stage_id"));
    }

    /**
     * askHuman从中立请求构造既有协议
     */
    @Test
    void askHumanBuildsExistingProtocolFromNeutralRequest() {
        var request = new QuestionInterventionRequest("call-1", List.of(
                new QuestionSpec("TEXT_INPUT", "Need input?", null, List.of())));
        var tree = JsonUtils.toTree(factory.askHuman(request, "invocation-1", "ask_human"));
        assertEquals("ASK_HUMAN", tree.get("event_type").asText());
        assertEquals("call-1", tree.at("/messages/0/tool_call_id").asText());
        assertEquals("invocation-1", tree.at("/context/invocation_id").asText());
    }

    /**
     * end精确保持空消息协议
     */
    @Test
    void endPreservesEmptyMessageProtocolExactly() {
        assertEquals("{\"event_type\":\"END\"}", JsonUtils.toJson(factory.end()));
        assertInstanceOf(EndMessage.class, factory.end());
    }
}
