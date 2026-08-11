package org.gemo.apex.kit;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.intervention.HumanSubmission;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.CancellationRegistration;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.protocol.event.AgentMessage;

final class KitFixtures {
    private static final CancellationToken TOKEN =
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
    static final ToolExecutionObserver OBSERVER =
            new ToolExecutionObserver() {
                @Override
                public void onEvent(AgentMessage event) {}

                @Override
                public CancellationToken cancellationToken() {
                    return TOKEN;
                }
            };

    private KitFixtures() {}

    static HookBinding binding(String name, List<String> tools, Map<String, Object> options) {
        return new HookBinding("binding-1", name, 10, true, tools, options);
    }

    static ToolCall call(String name, Map<String, Object> arguments) {
        return new ToolCall("call-1", name, 0, arguments, Map.of());
    }

    static PreToolCallContext pre(ToolCall call, HookBinding binding, HumanSubmission submission) {
        return new PreToolCallContext(
                "session-1", binding, call, "invocation-1", "intervention-1", submission);
    }

    static PostToolCallContext post(ToolResult result) {
        ToolCall call = call(result.toolName(), Map.of());
        return new PostToolCallContext(
                "session-1", binding("truncate", List.of("*"), Map.of()), call, result);
    }

    static ToolExecutionContext execution(HumanSubmission submission) {
        return new ToolExecutionContext(
                "session-1", 1, 1, "user-1", submission, null, TOKEN, Map.of());
    }

    static ToolExecutionContext execution(
            SharedDataStore sharedData, long turnNo, int iterationNo) {
        return new ToolExecutionContext(
                "session-1",
                turnNo,
                iterationNo,
                "user-1",
                null,
                null,
                TOKEN,
                sharedData,
                Map.of());
    }

    static ToolExecutionContext execution(long turnNo, int iterationNo) {
        return execution(SharedDataStores.create(), turnNo, iterationNo);
    }

    static ToolExecutionContext execution(Set<String> enabledSkills, Set<String> activatedSkills) {
        return new ToolExecutionContext(
                "session-1",
                1,
                1,
                "user-1",
                enabledSkills,
                activatedSkills,
                null,
                null,
                TOKEN,
                Map.of());
    }
}
