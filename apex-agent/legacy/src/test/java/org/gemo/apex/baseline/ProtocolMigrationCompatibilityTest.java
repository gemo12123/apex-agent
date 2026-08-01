package org.gemo.apex.baseline;

import org.gemo.apex.message.AskHumanMessage;
import org.gemo.apex.message.EndMessage;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.protocol.event.detail.AskHumanDetail;
import org.gemo.apex.util.JacksonUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolMigrationCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "stream-content.json", "ask-human.json", "tool-confirmation.json", "end.json",
            "plan-declared.json", "plan-change.json", "task-think-declared.json", "task-think-change.json"
    })
    void frozenLegacyGoldenFilesRoundTripThroughProtocol(String fileName) throws IOException {
        String expected = resource(fileName);
        org.gemo.apex.protocol.event.AgentMessage protocol = JacksonUtils.fromJson(
                expected, org.gemo.apex.protocol.event.AgentMessage.class);
        String actual = JacksonUtils.toJson(protocol);

        assertEquals(JacksonUtils.toTree(expected), JacksonUtils.toTree(actual));
        if ("end.json".equals(fileName)) {
            assertEquals(expected, actual);
        }
    }

    @Test
    void legacyAndProtocolStreamContentProduceTheSameJson() {
        Map<String, Object> context = Map.of("mode", "react", "content_id", "content-1");
        StreamContentMessage legacy = StreamContentMessage.builder()
                .context(context)
                .messages(List.of(new StreamContentMessage.ContentMessage("hello")))
                .build();
        org.gemo.apex.protocol.event.StreamContentMessage protocol =
                org.gemo.apex.protocol.event.StreamContentMessage.builder()
                        .context(context)
                        .messages(List.of(new org.gemo.apex.protocol.event.StreamContentMessage.ContentMessage("hello")))
                        .build();

        assertEquals(JacksonUtils.toTree(JacksonUtils.toJson(legacy)),
                JacksonUtils.toTree(JacksonUtils.toJson(protocol)));
    }

    @Test
    void legacyAndProtocolAskHumanProduceTheSameJson() {
        Map<String, Object> context = Map.of(
                "mode", "react", "executor", "ask_human", "invocation_id", "invocation-1");
        AskHumanMessage legacy = AskHumanMessage.builder()
                .context(context)
                .messages(List.of(AskHumanMessage.AskHumanDetail.builder()
                        .inputType("TEXT_INPUT").question("Need input?").options(List.of())
                        .toolCallId("call-ask-1").build()))
                .build();
        org.gemo.apex.protocol.event.AskHumanMessage protocol =
                org.gemo.apex.protocol.event.AskHumanMessage.builder()
                        .context(context)
                        .messages(List.of(AskHumanDetail.builder()
                                .inputType("TEXT_INPUT").question("Need input?").options(List.of())
                                .toolCallId("call-ask-1").build()))
                        .build();

        assertEquals(JacksonUtils.toTree(JacksonUtils.toJson(legacy)),
                JacksonUtils.toTree(JacksonUtils.toJson(protocol)));
    }

    @Test
    void legacyAndProtocolEndRemainExact() {
        assertEquals(JacksonUtils.toJson(EndMessage.builder().build()),
                JacksonUtils.toJson(org.gemo.apex.protocol.event.EndMessage.builder().build()));
        assertEquals("{\"event_type\":\"END\"}",
                JacksonUtils.toJson(org.gemo.apex.protocol.event.EndMessage.builder().build()));
    }

    private String resource(String fileName) throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("golden/protocol/" + fileName)) {
            if (input == null) {
                throw new IOException("缺少 legacy Golden File: " + fileName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }
}
