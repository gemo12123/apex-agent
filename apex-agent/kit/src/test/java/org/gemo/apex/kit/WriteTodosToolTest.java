package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.tool.WriteTodosTool;
import org.junit.jupiter.api.Test;

class WriteTodosToolTest {
    private final WriteTodosTool tool = new WriteTodosTool();

    /** 工具定义固定暴露完整列表参数和三种状态。 */
    @Test
    void exposesStableDefinitionAndTodoSchema() {
        assertEquals(WriteTodosTool.NAME, tool.definition().name());
        assertTrue(tool.definition().inputSchemaJson().contains("\"todos\""));
        assertTrue(tool.definition().inputSchemaJson().contains("\"pending\""));
        assertTrue(tool.definition().inputSchemaJson().contains("\"in_progress\""));
        assertTrue(tool.definition().inputSchemaJson().contains("\"completed\""));
    }

    /** 写入会整体替换列表，并声明Turn与Iteration两种清理策略。 */
    @Test
    void replacesWholeListAndDeclaresCleanupPolicies() {
        SharedDataStore store = SharedDataStores.create();
        ToolExecutionContext context = KitFixtures.execution(store, 1, 1);

        ToolResult result =
                tool.execute(call(todos("第一步", "pending")), context, KitFixtures.OBSERVER);

        assertTrue(result.content().contains("第一步"));
        assertEquals(
                List.of(Map.of("content", "第一步", "status", "pending")),
                store.get(WriteTodosTool.SHARED_DATA_KEY));
        assertEquals(
                SharedDataCleanupPolicy.TURN_END,
                store.entries().get(WriteTodosTool.SHARED_DATA_KEY).cleanupPolicy());
        assertEquals(
                SharedDataCleanupPolicy.ITERATION_END,
                store.entries().get(WriteTodosTool.ITERATION_WRITE_MARKER_KEY).cleanupPolicy());

        tool.execute(
                call(todos("第二步", "in_progress")),
                KitFixtures.execution(store, 1, 2),
                KitFixtures.OBSERVER);
        assertEquals(
                List.of(Map.of("content", "第二步", "status", "in_progress")),
                store.get(WriteTodosTool.SHARED_DATA_KEY));
    }

    /** 空列表显式清除Todo，但仍限制本Iteration再次写入。 */
    @Test
    void clearsTodosWithEmptyList() {
        SharedDataStore store = SharedDataStores.create();
        store.put(
                WriteTodosTool.SHARED_DATA_KEY,
                todos("旧任务", "pending"),
                SharedDataCleanupPolicy.TURN_END);

        ToolResult result =
                tool.execute(
                        call(List.of()), KitFixtures.execution(store, 2, 1), KitFixtures.OBSERVER);

        assertEquals("Todo 列表已清空。", result.content());
        assertFalse(store.containsKey(WriteTodosTool.SHARED_DATA_KEY));
        assertTrue(store.containsKey(WriteTodosTool.ITERATION_WRITE_MARKER_KEY));
    }

    /** 同一Iteration仅首次更新生效，旧Turn残留标记不影响新Turn。 */
    @Test
    void ignoresDuplicateInSameIterationButAcceptsNewTurn() {
        SharedDataStore store = SharedDataStores.create();
        ToolExecutionContext firstContext = KitFixtures.execution(store, 1, 1);
        tool.execute(call(todos("首次", "pending")), firstContext, KitFixtures.OBSERVER);

        ToolResult duplicate =
                tool.execute(call(todos("重复", "completed")), firstContext, KitFixtures.OBSERVER);
        assertTrue(duplicate.content().contains("未应用"));
        assertEquals(
                List.of(Map.of("content", "首次", "status", "pending")),
                store.get(WriteTodosTool.SHARED_DATA_KEY));

        tool.execute(
                call(todos("新Turn", "in_progress")),
                KitFixtures.execution(store, 2, 1),
                KitFixtures.OBSERVER);
        assertEquals(
                List.of(Map.of("content", "新Turn", "status", "in_progress")),
                store.get(WriteTodosTool.SHARED_DATA_KEY));
    }

    /** 缺少数组、非法项目或未知状态均拒绝执行。 */
    @Test
    void rejectsInvalidArgumentsAndWrongToolName() {
        ToolExecutionContext context = KitFixtures.execution(1, 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                KitFixtures.call(WriteTodosTool.NAME, Map.of()),
                                context,
                                KitFixtures.OBSERVER));
        assertThrows(
                IllegalArgumentException.class,
                () -> tool.execute(call(List.of("bad")), context, KitFixtures.OBSERVER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                call(List.of(Map.of("content", "任务", "status", "unknown"))),
                                context,
                                KitFixtures.OBSERVER));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        tool.execute(
                                KitFixtures.call("other", Map.of("todos", List.of())),
                                context,
                                KitFixtures.OBSERVER));
    }

    private ToolCall call(List<?> todos) {
        return KitFixtures.call(WriteTodosTool.NAME, Map.of("todos", todos));
    }

    private List<Map<String, Object>> todos(String content, String status) {
        return List.of(Map.of("content", content, "status", status));
    }
}
