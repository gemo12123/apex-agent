package org.gemo.apex.core.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.snapshot.PreparedToolCallDisposition;
import org.gemo.apex.common.snapshot.PreparedToolCallSnapshot;
import org.gemo.apex.common.snapshot.SuspendedToolBatch;
import org.gemo.apex.protocol.event.EndMessage;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.gemo.apex.protocol.event.TaskErrorMessage;
import org.junit.jupiter.api.Test;

class AgentEventFactoryTest {
    private final AgentEventFactory factory = new AgentEventFactory();

    @Test
    void invocationLifecycleUsesResolvedArgumentsAndFinalResult() {
        InvocationDeclaredMessage declared =
                factory.invocationDeclared(
                        "invocation-1", "weather", Map.of("city", "北京", "timeout", 3));
        var declaredTree = JsonUtils.toTree(declared);
        assertEquals("react", declaredTree.at("/context/mode").asText());
        assertEquals("invocation-1", declaredTree.at("/context/invocation_id").asText());
        assertEquals("weather", declaredTree.at("/context/executor").asText());
        assertEquals("tool", declaredTree.at("/messages/0/invocation_type").asText());
        assertEquals("none", declaredTree.at("/messages/0/click_effect").asText());
        assertEquals("json", declaredTree.at("/messages/0/render_type").asText());
        assertEquals(
                Map.of("city", "北京", "timeout", 3),
                JsonUtils.fromJson(declared.getMessages().getFirst().getContent(), Map.class));

        InvocationChangeMessage changed =
                factory.invocationChange("invocation-1", "weather", "晴", "COMPLETE");
        assertEquals(
                List.of("CONTENT_APPEND", "STATUS_CHANGE"),
                changed.getMessages().stream().map(it -> it.getChangeType()).toList());
        assertEquals("晴", changed.getMessages().getFirst().getContent());
        assertEquals("text", changed.getMessages().getFirst().getRenderType());
        assertEquals("COMPLETE", changed.getMessages().getLast().getStatus());
    }

    /** streamContent保持react与contentId且不产生stageId */
    @Test
    void streamContentPreservesReactAndContentIdWithoutStageId() {
        var tree = JsonUtils.toTree(factory.streamContent("content-1", "hello"));
        assertEquals("STREAM_CONTENT", tree.get("event_type").asText());
        assertEquals("react", tree.at("/context/mode").asText());
        assertEquals("content-1", tree.at("/context/content_id").asText());
        assertFalse(tree.get("context").has("stage_id"));
    }

    /** 人工介入批次从中立请求构造统一协议 */
    @Test
    void humanInterventionBuildsUnifiedProtocolFromNeutralRequest() {
        var request =
                new QuestionInterventionRequest(
                        "call-1",
                        List.of(new QuestionSpec("TEXT_INPUT", "Need input?", null, List.of())));
        var prepared =
                new PreparedToolCallSnapshot(
                        "call-1",
                        "invocation-1",
                        "ask_human",
                        0,
                        Map.of(),
                        Map.of(),
                        List.of(),
                        PreparedToolCallDisposition.INTERVENTION,
                        null,
                        request,
                        null);
        var tree =
                JsonUtils.toTree(
                        factory.humanIntervention(
                                new SuspendedToolBatch("session-1", 1, 1, List.of(prepared))));
        assertEquals("HUMAN_INTERVENTION", tree.get("event_type").asText());
        assertEquals("call-1", tree.at("/messages/0/tool_call_id").asText());
        assertEquals("invocation-1", tree.at("/messages/0/invocation_id").asText());
        assertEquals("TEXT_INPUT", tree.at("/messages/0/questions/0/input_type").asText());
    }

    /** end精确保持空消息协议 */
    @Test
    void endPreservesEmptyMessageProtocolExactly() {
        assertEquals("{\"event_type\":\"END\"}", JsonUtils.toJson(factory.end()));
        assertInstanceOf(EndMessage.class, factory.end());
    }

    /** taskError仅输出异常message，空message使用固定兜底 */
    @Test
    void taskErrorPublishesOnlyMessageWithFallback() {
        assertEquals(
                "{\"event_type\":\"TASK_ERROR\",\"context\":{\"mode\":\"react\"},\"messages\":[{\"message\":\"model down\"}]}",
                JsonUtils.toJson(factory.taskError(new IllegalStateException("model down"))));
        TaskErrorMessage fallback = factory.taskError(new IllegalStateException());
        assertEquals("Agent 执行失败", fallback.getMessages().getFirst().getMessage());
    }
}
