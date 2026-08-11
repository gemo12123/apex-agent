package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.hook.result.ContinuePreModelCall;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.kit.hook.TodoMiddleware;
import org.gemo.apex.kit.tool.WriteTodosTool;
import org.junit.jupiter.api.Test;

class TodoMiddlewareTest {
    private final TodoMiddleware middleware = new TodoMiddleware();

    /** 中间件声明稳定注册名和PRE_MODEL_CALL契约。 */
    @Test
    void exposesStablePreModelContract() {
        assertEquals(TodoMiddleware.REGISTRATION_NAME, middleware.name());
        assertEquals(HookPoint.PRE_MODEL_CALL, middleware.descriptor().hookPoint());
        assertEquals(PreModelCallContext.class, middleware.descriptor().contextType());
    }

    /** 没有Todo时仅临时注入使用规则，不改变请求消息。 */
    @Test
    void injectsRulesWithoutChangingMessages() {
        ModelRequest request = request(List.of(textMessage("普通消息")));

        ModelRequest replacement = apply(request, SharedDataStores.create());

        assertTrue(replacement.systemPrompt().contains("<todo_list_system>"));
        assertTrue(replacement.systemPrompt().contains("write_todos"));
        assertFalse(replacement.systemPrompt().contains("<system_reminder>"));
        assertEquals(request.messages(), replacement.messages());
        assertEquals(request.tools(), replacement.tools());
    }

    /** ToolCall仍在有效窗口中时无需重复注入当前列表。 */
    @Test
    void skipsReminderWhenWriteTodosCallIsStillVisible() {
        SharedDataStore store = todosStore("编写测试", "in_progress");
        AgentMessageEntry toolCall =
                message(
                        MessageRole.ASSISTANT,
                        MessageType.TOOL_CALLS,
                        "",
                        Map.of(
                                "toolCalls",
                                List.of(
                                        Map.of(
                                                "name",
                                                WriteTodosTool.NAME,
                                                "arguments",
                                                Map.of()))));

        ModelRequest replacement = apply(request(List.of(toolCall)), store);

        assertFalse(replacement.systemPrompt().contains("<system_reminder>"));
        assertFalse(replacement.systemPrompt().contains("编写测试"));
    }

    /** 压缩后结构化ToolCall消失时补回格式化Todo，文本提及工具名不算可见调用。 */
    @Test
    void restoresTodoContextWhenStructuredCallIsMissing() {
        SharedDataStore store = todosStore("编写测试", "in_progress");

        ModelRequest replacement = apply(request(List.of(textMessage("摘要中提到 write_todos"))), store);

        assertTrue(replacement.systemPrompt().contains("<system_reminder>"));
        assertTrue(replacement.systemPrompt().contains("- [in_progress] 编写测试"));
        assertEquals(1, replacement.messages().size());
    }

    /** 空列表或损坏条目不会生成空提醒。 */
    @Test
    void skipsReminderForEmptyOrMalformedState() {
        SharedDataStore empty = SharedDataStores.create();
        empty.put(WriteTodosTool.SHARED_DATA_KEY, List.of(), SharedDataCleanupPolicy.TURN_END);
        assertFalse(apply(request(List.of()), empty).systemPrompt().contains("<system_reminder>"));

        SharedDataStore malformed = SharedDataStores.create();
        malformed.put(
                WriteTodosTool.SHARED_DATA_KEY,
                List.of(Map.of("content", "缺少状态")),
                SharedDataCleanupPolicy.TURN_END);
        assertFalse(
                apply(request(List.of()), malformed).systemPrompt().contains("<system_reminder>"));
    }

    private ModelRequest apply(ModelRequest request, SharedDataStore store) {
        PreModelCallContext context =
                new PreModelCallContext(
                        "session-1",
                        KitFixtures.binding(TodoMiddleware.REGISTRATION_NAME, List.of(), Map.of()),
                        request,
                        store);
        ContinuePreModelCall result =
                assertInstanceOf(ContinuePreModelCall.class, middleware.apply(context));
        assertTrue(result.mutations().messageOperations().isEmpty());
        return result.patch().replacement();
    }

    private ModelRequest request(List<AgentMessageEntry> messages) {
        return new ModelRequest(
                "基础系统提示", messages, List.of(new WriteTodosTool().definition()), Map.of());
    }

    private SharedDataStore todosStore(String content, String status) {
        SharedDataStore store = SharedDataStores.create();
        store.put(
                WriteTodosTool.SHARED_DATA_KEY,
                List.of(Map.of("content", content, "status", status)),
                SharedDataCleanupPolicy.TURN_END);
        return store;
    }

    private AgentMessageEntry textMessage(String content) {
        return message(MessageRole.USER, MessageType.TEXT, content, Map.of());
    }

    private AgentMessageEntry message(
            MessageRole role, MessageType type, String content, Map<String, Object> payload) {
        return new AgentMessageEntry(
                "entry-1", "session-1", 1, 1, role, type, content, payload, Instant.EPOCH);
    }
}
