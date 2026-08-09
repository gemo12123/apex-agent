package org.gemo.apex.runtime.model.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.gemo.apex.common.agent.PrefixDeveloperMessage;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelStreamChunk;
import org.gemo.apex.common.tool.CancellationRegistration;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.extension.model.ModelStreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiModelGatewayTest {
    /** Spring AI请求严格按根系统、前置开发者消息、对话消息的顺序发送。 */
    @Test
    void sendsRootSystemThenPrefixDeveloperThenConversationMessages() {
        AtomicReference<Prompt> captured = new AtomicReference<>();
        ChatModel model =
                (ChatModel)
                        Proxy.newProxyInstance(
                                ChatModel.class.getClassLoader(),
                                new Class<?>[] {ChatModel.class},
                                (proxy, method, arguments) -> {
                                    if (method.getName().equals("stream")) {
                                        captured.set((Prompt) arguments[0]);
                                        return Flux.just(
                                                new ChatResponse(
                                                        List.of(
                                                                new Generation(
                                                                        new AssistantMessage(
                                                                                "完成")))));
                                    }
                                    if (method.getName().equals("toString")) {
                                        return "capturing-model";
                                    }
                                    throw new UnsupportedOperationException(method.toString());
                                });
        ModelRequest request =
                new ModelRequest(
                        "根系统",
                        List.of(
                                new PrefixDeveloperMessage(MessageRole.SYSTEM, "前置系统"),
                                new PrefixDeveloperMessage(MessageRole.USER, "前置用户")),
                        List.of(conversationUser()),
                        List.of(),
                        Map.of());

        new SpringAiModelGateway(model).stream(request, observer());

        List<org.springframework.ai.chat.messages.Message> messages =
                captured.get().getInstructions();
        assertEquals(4, messages.size());
        assertEquals("根系统", assertInstanceOf(SystemMessage.class, messages.get(0)).getText());
        assertEquals("前置系统", assertInstanceOf(SystemMessage.class, messages.get(1)).getText());
        assertEquals("前置用户", assertInstanceOf(UserMessage.class, messages.get(2)).getText());
        assertEquals("对话用户", assertInstanceOf(UserMessage.class, messages.get(3)).getText());
    }

    private AgentMessageEntry conversationUser() {
        return new AgentMessageEntry(
                "entry",
                "session",
                1,
                0,
                MessageRole.USER,
                MessageType.TEXT,
                "对话用户",
                Map.of(),
                Instant.EPOCH);
    }

    private ModelStreamObserver observer() {
        CancellationToken token =
                new CancellationToken() {
                    @Override
                    public boolean isCancellationRequested() {
                        return false;
                    }

                    @Override
                    public CancellationRegistration onCancel(Runnable command) {
                        return () -> {};
                    }
                };
        return new ModelStreamObserver() {
            @Override
            public void onChunk(ModelStreamChunk chunk) {}

            @Override
            public CancellationToken cancellationToken() {
                return token;
            }
        };
    }
}
