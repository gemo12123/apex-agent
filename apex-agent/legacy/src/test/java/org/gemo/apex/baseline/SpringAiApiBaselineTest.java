package org.gemo.apex.baseline;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringAiApiBaselineTest {

    @Test
    void criticalTypesLoadFromRecordedResolvedJars() {
        assertSource(ChatModel.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(Message.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(ChatResponse.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(AssistantMessage.ToolCall.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(ToolResponseMessage.ToolResponse.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(ChatOptions.class, "spring-ai-model-2.0.0-M1.jar");
        assertSource(ReactAgent.class, "spring-ai-alibaba-agent-framework-1.1.0.0-RC2.jar");
        assertSource(ApplicationContext.class, "spring-context-6.2.6.jar");
    }

    @Test
    void criticalApiSignaturesRemainAvailableToLegacyCode() throws Exception {
        assertEquals(ChatResponse.class, ChatModel.class.getMethod("call", Prompt.class).getReturnType());
        assertEquals(ChatOptions.class, ChatModel.class.getMethod("getDefaultOptions").getReturnType());
        assertEquals(String.class, Message.class.getMethod("getText").getReturnType());
        assertEquals(4, AssistantMessage.ToolCall.class.getRecordComponents().length);
        assertEquals(3, ToolResponseMessage.ToolResponse.class.getRecordComponents().length);
        assertTrue(Arrays.stream(ChatResponse.class.getConstructors())
                .anyMatch(constructor -> constructor.getParameterCount() == 1));
        Method copy = ChatOptions.class.getMethod("copy");
        assertTrue(ChatOptions.class.isAssignableFrom(copy.getReturnType()));
    }

    private void assertSource(Class<?> type, String expectedJar) {
        String source = type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        assertTrue(source.endsWith(expectedJar), () -> type.getName() + " 实际来源：" + source);
    }
}
