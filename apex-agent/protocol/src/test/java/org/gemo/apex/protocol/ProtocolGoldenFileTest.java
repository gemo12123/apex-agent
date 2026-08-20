package org.gemo.apex.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.ArtifactChangeMessage;
import org.gemo.apex.protocol.event.ArtifactDeclaredMessage;
import org.gemo.apex.protocol.event.EndMessage;
import org.gemo.apex.protocol.event.HumanInterventionMessage;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.gemo.apex.protocol.event.IterationEndMessage;
import org.gemo.apex.protocol.event.IterationStartMessage;
import org.gemo.apex.protocol.event.StreamContentMessage;
import org.gemo.apex.protocol.event.StreamThinkMessage;
import org.gemo.apex.protocol.event.TaskErrorMessage;
import org.gemo.apex.protocol.event.TurnEndMessage;
import org.gemo.apex.protocol.event.TurnStartMessage;
import org.gemo.apex.protocol.request.SessionStateView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProtocolGoldenFileTest {
    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .build();

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventGoldenFiles")
    void eventGoldenFileRoundTrip(
            String fileName, Class<? extends AgentMessage> expectedType, boolean runningEvent)
            throws IOException {
        String golden = resource(fileName);
        AgentMessage message = MAPPER.readValue(golden, AgentMessage.class);

        assertInstanceOf(expectedType, message);
        assertEquals(MAPPER.readTree(golden), MAPPER.valueToTree(message));
        if (runningEvent) {
            JsonNode node = MAPPER.readTree(golden);
            assertEquals("react", node.path("context").path("mode").asText());
            assertFalse(node.path("context").has("stage_id"));
        }

        JsonNode withUnknownField = MAPPER.readTree(golden).deepCopy();
        ((ObjectNode) withUnknownField).put("future_field", "ignored");
        assertInstanceOf(expectedType, MAPPER.treeToValue(withUnknownField, AgentMessage.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sessionGoldenFiles")
    void sessionStateGoldenFileRoundTrip(
            String fileName, Class<? extends AgentMessage> interactionType, String eventGoldenFile)
            throws IOException {
        String golden = resource(fileName);
        SessionStateView view = MAPPER.readValue(golden, SessionStateView.class);

        assertEquals(MAPPER.readTree(golden), MAPPER.valueToTree(view));
        if (interactionType == null) {
            assertEquals(null, view.pendingInteraction());
        } else {
            assertInstanceOf(interactionType, view.pendingInteraction());
            assertTrue(MAPPER.readTree(golden).has("sessionId"));
            assertFalse(MAPPER.readTree(golden).has("session_id"));
            assertEquals(
                    MAPPER.readTree(resource(eventGoldenFile)),
                    MAPPER.readTree(golden).path("pendingInteraction"));
        }
    }

    @Test
    void endIsAlwaysTheExactSingleFieldPayload() throws IOException {
        assertEquals(
                "{\"event_type\":\"END\"}",
                MAPPER.writeValueAsString(EndMessage.builder().build()));
        EndMessage withNulls = EndMessage.builder().context(null).messages(null).build();
        assertEquals("{\"event_type\":\"END\"}", MAPPER.writeValueAsString(withNulls));
    }

    @Test
    void unknownEventTypeFailsExplicitly() {
        assertThrows(
                IOException.class,
                () -> MAPPER.readValue("{\"event_type\":\"UNKNOWN\"}", AgentMessage.class));
    }

    @Test
    void remoteSseDataPayloadCanBeReadDirectlyAfterRemovingPrefix() throws IOException {
        String sse = "data:" + resource("invocation-declared.json") + "\n\n";
        AgentMessage message =
                MAPPER.readValue(sse.substring("data:".length()).trim(), AgentMessage.class);
        assertInstanceOf(InvocationDeclaredMessage.class, message);
    }

    @Test
    void protocolDoesNotIntroduceToolResultEventOrPayload() throws IllegalAccessException {
        Map<String, String> eventTypes = AgentEventInventory.eventTypes();
        assertFalse(eventTypes.containsValue("TOOL_RESULT"));
        assertFalse(eventTypes.containsValue("TOOL_REJECTED"));
        assertFalse(resourceUnchecked("human-intervention.json").contains("\"code\""));
        assertFalse(resourceUnchecked("human-intervention.json").contains("\"payload\""));
    }

    static Stream<Arguments> eventGoldenFiles() {
        return Stream.of(
                Arguments.of("stream-content.json", StreamContentMessage.class, true),
                Arguments.of("human-intervention.json", HumanInterventionMessage.class, true),
                Arguments.of("task-error.json", TaskErrorMessage.class, true),
                Arguments.of("end.json", EndMessage.class, false),
                Arguments.of("stream-think.compat.json", StreamThinkMessage.class, false),
                Arguments.of("invocation-declared.json", InvocationDeclaredMessage.class, true),
                Arguments.of(
                        "invocation-declared-nested.json", InvocationDeclaredMessage.class, true),
                Arguments.of("invocation-change.json", InvocationChangeMessage.class, true),
                Arguments.of("artifact-declared.compat.json", ArtifactDeclaredMessage.class, false),
                Arguments.of("artifact-change.compat.json", ArtifactChangeMessage.class, false),
                Arguments.of("turn-start.json", TurnStartMessage.class, true),
                Arguments.of("iteration-start.json", IterationStartMessage.class, true),
                Arguments.of("iteration-end.json", IterationEndMessage.class, true),
                Arguments.of("turn-end.json", TurnEndMessage.class, true));
    }

    static Stream<Arguments> sessionGoldenFiles() {
        return Stream.of(
                Arguments.of(
                        "session-state-human-intervention.json",
                        HumanInterventionMessage.class,
                        "human-intervention.json"),
                Arguments.of("session-state-completed.json", null, null));
    }

    private static String resource(String fileName) throws IOException {
        try (InputStream input =
                ProtocolGoldenFileTest.class
                        .getClassLoader()
                        .getResourceAsStream("golden/protocol/" + fileName)) {
            if (input == null) {
                throw new IOException("缺少 Golden File: " + fileName);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).stripTrailing();
        }
    }

    private static String resourceUnchecked(String fileName) {
        try {
            return resource(fileName);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
