package org.gemo.apex.kit.hook;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.hook.operation.AppendMessage;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.MessageOperation;
import org.gemo.apex.common.hook.operation.RemoveMessage;
import org.gemo.apex.common.hook.operation.ReplaceMessage;
import org.gemo.apex.common.hook.operation.ToolActivationDelta;
import org.gemo.apex.common.hook.result.ContinuePreModelCall;
import org.gemo.apex.common.hook.result.PreModelCallHookResult;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.tool.WriteTodosTool;

/** 为模型注入 Todo 使用规则，并在压缩导致 ToolCall 丢失后补回当前列表。 */
public final class TodoMiddleware
        implements LifecycleHook<PreModelCallContext, PreModelCallHookResult> {
    public static final String REGISTRATION_NAME = "todoMiddleware";
    static final String CONTEXT_KIND_KEY = "apex.kind";
    static final String CONTEXT_KIND = "todo_context";
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
        List<Map<String, Object>> todos =
                readTodos(context.sharedData().get(WriteTodosTool.SHARED_DATA_KEY));
        StringBuilder content = new StringBuilder(SYSTEM_PROMPT.strip());
        if (!todos.isEmpty()) {
            appendBlock(content, reminder(todos));
        }
        Map<String, Object> payload = Map.of(CONTEXT_KIND_KEY, CONTEXT_KIND);
        List<AgentMessageEntry> contexts =
                context.request().messages().stream()
                        .filter(this::isTodoContext)
                        .sorted(Comparator.comparingLong(AgentMessageEntry::sortNo))
                        .toList();
        List<MessageOperation> operations = new ArrayList<>();
        if (contexts.isEmpty()) {
            operations.add(
                    new AppendMessage(
                            "todo-context-append",
                            MessageRole.SYSTEM,
                            MessageType.TEXT,
                            content.toString(),
                            payload));
        } else {
            AgentMessageEntry retained = contexts.getLast();
            for (int index = 0; index < contexts.size() - 1; index++) {
                AgentMessageEntry duplicate = contexts.get(index);
                operations.add(
                        new RemoveMessage(
                                "todo-context-remove-" + duplicate.entryId(), duplicate.entryId()));
            }
            if (!java.util.Objects.equals(retained.content(), content.toString())
                    || !retained.payload().equals(payload)
                    || retained.role() != MessageRole.SYSTEM
                    || retained.messageType() != MessageType.TEXT) {
                operations.add(
                        new ReplaceMessage(
                                "todo-context-replace",
                                retained.entryId(),
                                MessageRole.SYSTEM,
                                MessageType.TEXT,
                                content.toString(),
                                payload));
            }
        }
        return new ContinuePreModelCall(new HookMutations(operations, ToolActivationDelta.none()));
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

    private boolean isTodoContext(AgentMessageEntry message) {
        return message.role() == MessageRole.SYSTEM
                && message.messageType() == MessageType.TEXT
                && CONTEXT_KIND.equals(message.payload().get(CONTEXT_KIND_KEY));
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
