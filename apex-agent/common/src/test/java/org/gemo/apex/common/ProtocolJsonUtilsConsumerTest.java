package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.request.SessionStateView;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolJsonUtilsConsumerTest {
    /** 产品JsonUtils应消费全部事件GoldenFile */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "artifact-change.compat.json",
                "artifact-declared.compat.json",
                "end.json",
                "human-intervention.json",
                "invocation-change.json",
                "invocation-declared.json",
                "plan-change.compat.json",
                "plan-declared.compat.json",
                "stream-content.json",
                "stream-think.compat.json",
                "task-think-change.compat.json",
                "task-think-declared.compat.json"
            })
    void productJsonUtilsConsumesAllEventGoldenFiles(String fileName) throws IOException {
        String golden = resource(fileName);
        AgentMessage message = JsonUtils.fromJson(golden, AgentMessage.class);
        assertEquals(JsonUtils.parseTree(golden), JsonUtils.toTree(message));
    }

    /** 产品JsonUtils应消费全部会话GoldenFile */
    @ParameterizedTest
    @ValueSource(
            strings = {"session-state-completed.json", "session-state-human-intervention.json"})
    void productJsonUtilsConsumesAllSessionGoldenFiles(String fileName) throws IOException {
        String golden = resource(fileName);
        SessionStateView view = JsonUtils.fromJson(golden, SessionStateView.class);
        assertEquals(JsonUtils.parseTree(golden), JsonUtils.toTree(view));
    }

    private static String resource(String fileName) throws IOException {
        try (InputStream input =
                ProtocolJsonUtilsConsumerTest.class
                        .getClassLoader()
                        .getResourceAsStream("golden/protocol/" + fileName)) {
            if (input == null) {
                throw new IOException("缺少 Golden File: " + fileName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }
}
