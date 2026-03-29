package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ModelResponseStreamerTest {

    @Mock
    private ChatModel chatModel;

    private ModelResponseStreamer modelResponseStreamer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        modelResponseStreamer = new ModelResponseStreamer(chatModel);
    }

    @Test
    void streamShouldAggregatePlainTextResponse() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);
        context.setExecutionMode(ModeEnum.REACT);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        context.setSseEmitter(emitter);

        AssistantMessage assistantMessage = new AssistantMessage("done");
        ChatResponse response = new ChatResponse(List.of(new Generation(assistantMessage)));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        ChatResponse result = modelResponseStreamer.stream(new Prompt(List.of()), context);

        assertEquals("done", result.getResult().getOutput().getText());
        assertTrue(emitter.payloads.stream().anyMatch(payload -> payload.contains("STREAM_CONTENT")));
        assertTrue(emitter.payloads.stream().anyMatch(payload -> payload.contains("done")));
    }

    @Test
    void streamShouldReturnToolCallResponseWithoutThrowing() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);

        AssistantMessage assistantMessage = AssistantMessage.builder()
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "meeting_tool", "{}")))
                .build();
        ChatResponse response = new ChatResponse(List.of(new Generation(assistantMessage)));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        ChatResponse result = modelResponseStreamer.stream(new Prompt(List.of()), context);

        assertEquals(1, result.getResult().getOutput().getToolCalls().size());
    }

    @Test
    void streamShouldFailOnEmptyResponseWithoutToolCalls() {
        SuperAgentContext context = new SuperAgentContext();
        context.setCurrentStage(SuperAgentContext.Stage.EXECUTION);

        ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(response));

        assertThrows(IllegalStateException.class, () -> modelResponseStreamer.stream(new Prompt(List.of()), context));
    }

    private static class CapturingSseEmitter extends SseEmitter {
        private final List<String> payloads = new ArrayList<>();

        @Override
        public synchronized void send(Object object) throws IOException {
            payloads.add(String.valueOf(object));
        }
    }
}
