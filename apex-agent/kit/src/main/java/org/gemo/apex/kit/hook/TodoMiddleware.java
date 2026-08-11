package org.gemo.apex.kit.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ModelRequestPatch;
import org.gemo.apex.common.hook.result.ContinuePreModelCall;
import org.gemo.apex.common.hook.result.PreModelCallHookResult;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.tool.WriteTodosTool;

/** 为模型注入 Todo 使用规则，并在压缩导致 ToolCall 丢失后补回当前列表。 */
public final class TodoMiddleware
        implements LifecycleHook<PreModelCallContext, PreModelCallHookResult> {
    public static final String REGISTRATION_NAME = "todoMiddleware";
    static final String SYSTEM_PROMPT =
            """
            <todo_list_system>
            你可以使用 `write_todos` 工具管理复杂的多步骤任务。
            - 完成每一步后立即更新状态，不要批量标记完成。
            - 通常只保留一个 `in_progress` 项；确实并行执行时可以有多个。
            - 工作过程中实时更新列表，让任务进度始终准确。
            - 简单任务（少于三个步骤）不要使用该工具，直接完成即可。
            - 同一次模型响应最多调用一次 `write_todos`，每次调用都必须提交完整列表。
            </todo_list_system>
            """;
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.PRE_MODEL_CALL,
                    PreModelCallContext.class,
                    PreModelCallHookResult.class);

    @Override
    public String name() {
        return REGISTRATION_NAME;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PreModelCallHookResult apply(PreModelCallContext context) {
        ModelRequest request = context.request();
        StringBuilder systemPrompt = new StringBuilder(request.systemPrompt());
        appendBlock(systemPrompt, SYSTEM_PROMPT);

        List<Map<String, Object>> todos =
                readTodos(context.sharedData().get(WriteTodosTool.SHARED_DATA_KEY));
        if (!todos.isEmpty() && !containsWriteTodosCall(request.messages())) {
            appendBlock(systemPrompt, reminder(todos));
        }

        ModelRequest replacement =
                new ModelRequest(
                        systemPrompt.toString(),
                        request.prefixDeveloperMessages(),
                        request.messages(),
                        request.tools(),
                        request.options());
        return new ContinuePreModelCall(HookMutations.none(), new ModelRequestPatch(replacement));
    }

    private void appendBlock(StringBuilder target, String block) {
        if (target.indexOf(block) >= 0) {
            return;
        }
        if (!target.isEmpty()) {
            target.append("\n\n");
        }
        target.append(block.strip());
    }

    private boolean containsWriteTodosCall(List<AgentMessageEntry> messages) {
        for (AgentMessageEntry message : messages) {
            if (message.role() != MessageRole.ASSISTANT
                    || message.messageType() != MessageType.TOOL_CALLS) {
                continue;
            }
            Object rawCalls = message.payload().get("toolCalls");
            if (!(rawCalls instanceof List<?> calls)) {
                continue;
            }
            for (Object rawCall : calls) {
                if (rawCall instanceof Map<?, ?> call
                        && WriteTodosTool.NAME.equals(call.get("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Map<String, Object>> readTodos(Object raw) {
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> todos = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> item)
                    || !(item.get("content") instanceof String content)
                    || !(item.get("status") instanceof String status)) {
                continue;
            }
            todos.add(Map.of("content", content, "status", status));
        }
        return List.copyOf(todos);
    }

    private String reminder(List<Map<String, Object>> todos) {
        String items =
                todos.stream()
                        .map(todo -> "- [" + todo.get("status") + "] " + todo.get("content"))
                        .reduce((left, right) -> left + "\n" + right)
                        .orElse("");
        return """
                <system_reminder>
                先前创建的 Todo 列表已不在当前消息窗口中，但仍然有效：

                %s

                请继续按照该列表工作，并在任一项目状态变化时调用 `write_todos` 提交完整列表。
                </system_reminder>
                """
                .formatted(items);
    }
}
