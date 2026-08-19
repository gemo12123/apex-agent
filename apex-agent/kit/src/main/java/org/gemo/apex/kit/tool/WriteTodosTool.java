package org.gemo.apex.kit.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;

/** 在当前 Turn 的共享数据中维护结构化 Todo 列表。 */
public final class WriteTodosTool implements AgentTool {
    public static final String NAME = "write_todos";
    public static final String TODOS_ARGUMENT = "todos";
    public static final String SHARED_DATA_KEY = "apex.todo.items";
    public static final String ITERATION_WRITE_MARKER_KEY = "apex.todo.write-marker";
    private static final Set<String> STATUSES = Set.of("pending", "in_progress", "completed");
    private static final ToolDefinition DEFINITION =
            new ToolDefinition(
                    NAME,
                    "创建或整体更新复杂任务的结构化 Todo 列表，仅适用于包含三个及以上步骤的工作",
                    "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"todos\"],\"properties\":{\"todos\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"content\",\"status\"],\"properties\":{\"content\":{\"type\":\"string\"},\"status\":{\"type\":\"string\",\"enum\":[\"pending\",\"in_progress\",\"completed\"]}}}}}}",
                    Map.of());

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(
            ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
        if (!NAME.equals(call.name())) {
            throw new IllegalArgumentException("WriteTodosTool 只能执行 write_todos 调用");
        }
        if (alreadyWrittenInCurrentIteration(context)) {
            return result(call, "同一模型响应只能调用一次 write_todos，本次更新未应用。");
        }

        List<Map<String, Object>> todos = parseTodos(call.arguments().get(TODOS_ARGUMENT));
        if (todos.isEmpty()) {
            context.sharedData().remove(SHARED_DATA_KEY);
        } else {
            context.sharedData().put(SHARED_DATA_KEY, todos, SharedDataCleanupPolicy.TURN_END);
        }
        context.sharedData()
                .put(
                        ITERATION_WRITE_MARKER_KEY,
                        Map.of("turnNo", context.turnNo(), "iterationNo", context.iterationNo()),
                        SharedDataCleanupPolicy.ITERATION_END);
        return result(call, todos.isEmpty() ? "Todo 列表已清空。" : "Todo 列表已更新：\n" + format(todos));
    }

    private boolean alreadyWrittenInCurrentIteration(ToolExecutionContext context) {
        Object raw = context.sharedData().get(ITERATION_WRITE_MARKER_KEY);
        if (!(raw instanceof Map<?, ?> marker)) {
            return false;
        }
        return numberEquals(marker.get("turnNo"), context.turnNo())
                && numberEquals(marker.get("iterationNo"), context.iterationNo());
    }

    private boolean numberEquals(Object raw, long expected) {
        return raw instanceof Number number && number.longValue() == expected;
    }

    private List<Map<String, Object>> parseTodos(Object raw) {
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("write_todos.todos 必须是数组");
        }
        List<Map<String, Object>> todos = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            if (!(value instanceof Map<?, ?> item)) {
                throw new IllegalArgumentException("write_todos.todos[" + index + "] 必须是对象");
            }
            Object rawContent = item.get("content");
            Object rawStatus = item.get("status");
            if (!(rawContent instanceof String content)) {
                throw new IllegalArgumentException(
                        "write_todos.todos[" + index + "].content 必须是字符串");
            }
            if (!(rawStatus instanceof String status) || !STATUSES.contains(status)) {
                throw new IllegalArgumentException(
                        "write_todos.todos["
                                + index
                                + "].status 必须是 pending、in_progress 或 completed");
            }
            Map<String, Object> todo = new LinkedHashMap<>();
            todo.put("content", content);
            todo.put("status", status);
            todos.add(todo);
        }
        return List.copyOf(todos);
    }

    private String format(List<Map<String, Object>> todos) {
        return todos.stream()
                .map(todo -> "- [" + todo.get("status") + "] " + todo.get("content"))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private ToolResult result(ToolCall call, String content) {
        return new ToolResult(call.toolCallId(), call.name(), content, Map.of());
    }
}
