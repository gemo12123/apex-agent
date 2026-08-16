package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PreModelCallContext;
import org.gemo.apex.common.hook.operation.AppendMessage;
import org.gemo.apex.common.hook.operation.MessageOperation;
import org.gemo.apex.common.hook.operation.RemoveMessage;
import org.gemo.apex.common.hook.operation.ReplaceMessage;
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

    /** 没有Todo上下文时追加一条持久化规则消息。 */
    @Test
    void appendsPersistentRulesWhenContextIsMissing() {
        ModelRequest request = request(List.of(textMessage("普通消息")));

        List<MessageOperation> operations = apply(request, SharedDataStores.create());

        AppendMessage append = assertInstanceOf(AppendMessage.class, operations.getFirst());
        assertEquals(MessageRole.SYSTEM, append.role());
        assertEquals(MessageType.TEXT, append.messageType());
        assertTrue(append.content().contains("<todo_list_system>"));
        assertFalse(append.content().contains("<system_reminder>"));
        assertEquals("todo_context", append.payload().get("apex.kind"));
    }

    /** 当前Todo会进入持久化上下文，而不是依赖请求级system prompt拼接。 */
    @Test
    void appendsCurrentTodosToPersistentContext() {
        SharedDataStore store = todosStore("编写测试", "in_progress");

        AppendMessage append =
                assertInstanceOf(
                        AppendMessage.class,
                        apply(request(List.of(textMessage("普通消息"))), store).getFirst());

        assertTrue(append.content().contains("<system_reminder>"));
        assertTrue(append.content().contains("- [in_progress] 编写测试"));
    }

    /** 已有标记内容变化时按entryId替换并保持单条。 */
    @Test
    void replacesExistingContextWhenTodoStateChanges() {
        SharedDataStore store = todosStore("编写测试", "in_progress");
        AgentMessageEntry existing =
                message("todo-1", 2, MessageRole.SYSTEM, MessageType.TEXT, "旧内容", todoPayload());

        ReplaceMessage replace =
                assertInstanceOf(
                        ReplaceMessage.class, apply(request(List.of(existing)), store).getFirst());

        assertEquals("todo-1", replace.targetEntryId());
        assertTrue(replace.content().contains("编写测试"));
    }

    /** Todo清空后把旧状态替换为静态规则，避免继续误导模型。 */
    @Test
    void clearsOldTodoStateWhenListBecomesEmpty() {
        AgentMessageEntry existing =
                message(
                        "todo-1",
                        2,
                        MessageRole.SYSTEM,
                        MessageType.TEXT,
                        "<system_reminder>旧 Todo</system_reminder>",
                        todoPayload());

        ReplaceMessage replace =
                assertInstanceOf(
                        ReplaceMessage.class,
                        apply(request(List.of(existing)), SharedDataStores.create()).getFirst());

        assertTrue(replace.content().contains("<todo_list_system>"));
        assertFalse(replace.content().contains("<system_reminder>"));
    }

    /** 多条活动标记保留最新一条并移除较旧重复项。 */
    @Test
    void removesDuplicateContextsAndKeepsLatest() {
        AgentMessageEntry old =
                message("todo-old", 2, MessageRole.SYSTEM, MessageType.TEXT, "旧内容", todoPayload());
        AgentMessageEntry latest =
                message(
                        "todo-latest",
                        3,
                        MessageRole.SYSTEM,
                        MessageType.TEXT,
                        "仍是旧内容",
                        todoPayload());

        List<MessageOperation> operations =
                apply(request(List.of(old, latest)), todosStore("编写测试", "completed"));

        assertEquals(2, operations.size());
        RemoveMessage remove = assertInstanceOf(RemoveMessage.class, operations.get(0));
        ReplaceMessage replace = assertInstanceOf(ReplaceMessage.class, operations.get(1));
        assertEquals("todo-old", remove.targetEntryId());
        assertEquals("todo-latest", replace.targetEntryId());
    }

    /** 标记消息被压缩出活动窗口后，根据共享数据重新追加最新上下文。 */
    @Test
    void restoresContextAfterCompressionRemovesMarker() {
        AppendMessage append =
                assertInstanceOf(
                        AppendMessage.class,
                        apply(
                                        request(List.of(textMessage("累计摘要"))),
                                        todosStore("继续实现", "in_progress"))
                                .getFirst());

        assertTrue(append.content().contains("继续实现"));
    }

    /** 内容一致时不产生写操作。 */
    @Test
    void skipsWriteWhenPersistentContextIsCurrent() {
        AppendMessage expected =
                assertInstanceOf(
                        AppendMessage.class,
                        apply(request(List.of()), todosStore("编写测试", "in_progress")).getFirst());
        AgentMessageEntry existing =
                message(
                        "todo-1",
                        2,
                        expected.role(),
                        expected.messageType(),
                        expected.content(),
                        expected.payload());

        assertTrue(apply(request(List.of(existing)), todosStore("编写测试", "in_progress")).isEmpty());
    }

    /** 损坏的Todo条目按空列表处理。 */
    @Test
    void treatsMalformedStateAsEmpty() {
        SharedDataStore malformed = SharedDataStores.create();

        malformed.put(
                WriteTodosTool.SHARED_DATA_KEY,
                List.of(Map.of("content", "缺少状态")),
                SharedDataCleanupPolicy.TURN_END);

        AppendMessage append =
                assertInstanceOf(
                        AppendMessage.class, apply(request(List.of()), malformed).getFirst());
        assertFalse(append.content().contains("<system_reminder>"));
    }

    private List<MessageOperation> apply(ModelRequest request, SharedDataStore store) {
        PreModelCallContext context =
                new PreModelCallContext(
                        "session-1",
                        KitFixtures.binding(TodoMiddleware.REGISTRATION_NAME, List.of(), Map.of()),
                        request,
                        store);
        ContinuePreModelCall result =
                assertInstanceOf(ContinuePreModelCall.class, middleware.apply(context));
        return result.mutations().messageOperations();
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
        return message("entry-1", 1, MessageRole.USER, MessageType.TEXT, content, Map.of());
    }

    private AgentMessageEntry message(
            String entryId,
            long sortNo,
            MessageRole role,
            MessageType type,
            String content,
            Map<String, Object> payload) {
        return new AgentMessageEntry(
                entryId, "session-1", 1, sortNo, role, type, content, payload, Instant.EPOCH);
    }

    private Map<String, Object> todoPayload() {
        return Map.of("apex.kind", "todo_context");
    }
}
