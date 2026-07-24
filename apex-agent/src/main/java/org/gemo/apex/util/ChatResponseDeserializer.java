package org.gemo.apex.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChatResponseDeserializer extends JsonDeserializer<ChatResponse> {

    @Override
    public ChatResponse deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        JsonNode root = mapper.readTree(parser);
        List<Generation> generations = new ArrayList<>();

        JsonNode results = root.get("results");
        if (results != null && results.isArray()) {
            for (JsonNode result : results) {
                addGeneration(mapper, result, generations);
            }
        } else {
            addGeneration(mapper, root.get("result"), generations);
        }
        return new ChatResponse(generations);
    }

    private void addGeneration(ObjectMapper mapper, JsonNode result, List<Generation> generations)
            throws IOException {
        if (result == null || result.isNull()) {
            return;
        }
        JsonNode output = result.get("output");
        if (output == null || output.isNull()) {
            return;
        }
        Message message = mapper.treeToValue(output, Message.class);
        if (message instanceof AssistantMessage assistantMessage) {
            generations.add(new Generation(assistantMessage));
        }
    }
}
