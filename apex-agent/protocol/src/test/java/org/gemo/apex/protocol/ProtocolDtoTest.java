package org.gemo.apex.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.gemo.apex.protocol.event.AgentEventType;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.RequestType;
import org.junit.jupiter.api.Test;

class ProtocolDtoTest {
    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    .serializationInclusion(JsonInclude.Include.NON_NULL)
                    .build();

    @Test
    void chatRequestKeepsLegacyDefaultsAndHumanResponseShape() {
        ChatRequest request = new ChatRequest();
        assertEquals(RequestType.NEW, request.getType());
        assertEquals("default_agent", request.getAgentKey());

        request.setType(RequestType.HUMAN_RESPONSE);
        request.setHumanResponse(
                Map.of(
                        "call-1",
                        Map.of("interaction_type", "ASK_HUMAN", "answers", Map.of("name", "A"))));
        assertTrue(request.getHumanResponse().containsKey("call-1"));
    }

    @Test
    void eventConstantsAndPolymorphicSubtypesAreOneToOne() throws IllegalAccessException {
        Set<String> constants = Set.copyOf(AgentEventInventory.eventTypes().values());
        Set<String> subtypeNames =
                Arrays.stream(AgentMessage.class.getAnnotation(JsonSubTypes.class).value())
                        .map(JsonSubTypes.Type::name)
                        .collect(Collectors.toSet());

        assertEquals(constants, subtypeNames);
        assertEquals("STREAM_CONTENT", AgentEventType.STREAM_CONTENT);
        assertEquals("TASK_ERROR", AgentEventType.TASK_ERROR);
        assertEquals("HUMAN_INTERVENTION", AgentEventType.HUMAN_INTERVENTION);
    }

    @Test
    void chatRequestKeepsCamelCaseHttpFields() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setQuery("hello");
        request.setSessionId("session-1");
        request.setAgentKey("codex");
        request.setType(RequestType.HUMAN_RESPONSE);
        request.setHumanResponse(Map.of("call-1", Map.of("interaction_type", "ASK_HUMAN")));

        assertEquals(
                MAPPER.readTree(
                        """
                {"query":"hello","sessionId":"session-1","type":"HUMAN_RESPONSE","agentKey":"codex",
                 "humanResponse":{"call-1":{"interaction_type":"ASK_HUMAN"}}}
                """),
                MAPPER.valueToTree(request));
    }
}
