package org.gemo.apex.protocol;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.gemo.apex.protocol.event.AgentMessage;
import org.gemo.apex.protocol.event.HumanInterventionMessage;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;
import org.gemo.apex.protocol.request.ChatRequest;
import org.gemo.apex.protocol.request.SessionStateView;
import org.junit.jupiter.api.Test;

class ProtocolDependencyTest {
    private static final List<String> FORBIDDEN =
            List.of(
                    "ApexAgentContext",
                    "HookContext",
                    "org/springframework/ai",
                    "SseEmitter",
                    "jakarta/servlet",
                    "javax/servlet",
                    "org/springframework/data",
                    "mybatis");

    @Test
    void protocolBytecodeDoesNotReferenceRuntimeOrInfrastructureTypes() throws IOException {
        for (Class<?> type :
                List.of(
                        AgentMessage.class,
                        ChatRequest.class,
                        SessionStateView.class,
                        HumanInterventionMessage.class,
                        ToolConfirmationDetail.class)) {
            String bytecode = classBytes(type);
            for (String forbidden : FORBIDDEN) {
                assertFalse(bytecode.contains(forbidden), type.getName() + " 引用了 " + forbidden);
            }
        }
    }

    private String classBytes(Class<?> type) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream input = type.getResourceAsStream(resource)) {
            return new String(input.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
