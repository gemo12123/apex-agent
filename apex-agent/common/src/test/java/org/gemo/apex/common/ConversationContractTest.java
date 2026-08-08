package org.gemo.apex.common;

import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConversationContractTest {
    private interface ConversationRepositorySignature {
        List<AgentMessageEntry> load(ConversationQuery query);
    }

    private interface ConversationWindowManagerSignature {
        ConversationWindow prepare(ConversationWindowRequest request);
    }

    /**
     * 下游冻结接口签名应能只使用common类型编译
     */
    @Test
    void allowsFrozenDownstreamInterfaceSignaturesToCompileUsingOnlyCommonTypes() {
        ConversationRepositorySignature repository = query -> messages();
        ConversationWindowManagerSignature manager = request -> {
            List<AgentMessageEntry> loaded = repository.load(request.query());
            return new ConversationWindow(request.query().sessionId(), loaded, 0L, 1L);
        };

        ConversationWindow window = manager.prepare(new ConversationWindowRequest(
                new ConversationQuery("session-1"), 100, 10));

        assertEquals(2, window.messages().size());
    }

    /**
     * 查询和窗口请求应具有明确边界
     */
    @Test
    void definesExplicitBoundariesForQueriesAndWindowRequests() {
        ConversationQuery query = new ConversationQuery("session-1");
        ConversationWindowRequest request = new ConversationWindowRequest(query, 100, 10);
        List<AgentMessageEntry> messages = messages();

        ConversationWindow window = new ConversationWindow("session-1", messages, 0L, 1L);

        assertEquals(query, request.query());
        assertEquals(messages, window.messages());
        assertThrows(IllegalArgumentException.class,
                () -> new ConversationWindowRequest(query, 10, 11));
        assertThrows(IllegalArgumentException.class,
                () -> new ConversationWindow("session-1", messages, 1L, 0L));
    }

    /**
     * 压缩检查应完整记录分项估算阈值保留窗口和触发上下文
     */
    @Test
    void recordsCompleteCompactionCheckEstimateThresholdRetainedWindowAndTriggerContext() {
        List<AgentMessageEntry> mutableMessages = new ArrayList<>(messages());
        ConversationCompactionTrigger trigger = new ConversationCompactionTrigger(
                "session-1", 1, 1, "TOKEN_THRESHOLD");

        ConversationCompactionCheck check = new ConversationCompactionCheck(
                mutableMessages,
                20, 80,
                10, 40,
                5, 20,
                35, 140,
                32, 128,
                1, trigger);
        mutableMessages.clear();

        assertEquals(2, check.messages().size());
        assertEquals(35, check.totalTokenEstimate());
        assertEquals(140, check.totalCharacterEstimate());
        assertEquals(32, check.tokenThreshold());
        assertEquals(128, check.characterThreshold());
        assertEquals(1, check.retainMessageCount());
        assertEquals(trigger, check.triggerContext());
        assertThrows(IllegalArgumentException.class, () -> new ConversationCompactionCheck(
                messages(), 20, 80, 10, 40, 5, 20, 34, 140,
                32, 128, 1, trigger));
    }

    /**
     * 请求结果和提交应保持保留消息及metadata并可往返
     */
    @Test
    void preservesRetainedMessagesAndMetadataInRequestsResultsAndCommitsRoundTrip() {
        List<AgentMessageEntry> source = messages();
        Map<String, Object> nestedMetadata = new LinkedHashMap<>();
        nestedMetadata.put("redacted", new ArrayList<>(List.of("email")));
        ConversationCompactionRequest request = new ConversationCompactionRequest(
                "session-1", "compaction-1", source, List.of(source.getLast()), nestedMetadata);
        ConversationCompactionResult result = new ConversationCompactionResult(
                "compaction-1", "summary", request.retainedMessages(), Map.of("model", "summary-model"));
        ConversationCompactionCommit commit = new ConversationCompactionCommit(
                "session-1", "compaction-1", 0, 1, result.summary(),
                List.of(source.getLast().entryId()), source);

        ConversationCompactionRequest requestCopy = JsonUtils.deepCopy(
                request, ConversationCompactionRequest.class);
        ConversationCompactionResult resultCopy = JsonUtils.deepCopy(
                result, ConversationCompactionResult.class);
        ConversationCompactionCommit commitCopy = JsonUtils.deepCopy(
                commit, ConversationCompactionCommit.class);

        ((List<String>) nestedMetadata.get("redacted")).add("phone");
        assertEquals(request, requestCopy);
        assertEquals(result, resultCopy);
        assertEquals(commit, commitCopy);
        assertEquals(List.of("email"), request.metadata().get("redacted"));
    }

    /**
     * 压缩对象应拒绝越界保留消息和不连续身份
     */
    @Test
    void rejectsCompactionObjectsWithOutOfBoundsRetainedMessagesOrDiscontinuousIdentities() {
        List<AgentMessageEntry> source = messages();
        AgentMessageEntry foreign = new AgentMessageEntry("foreign", "session-2", 1, 2,
                MessageRole.USER, MessageType.TEXT, "foreign", Map.of(), CommonFixtures.NOW);

        assertThrows(IllegalArgumentException.class, () -> new ConversationCompactionRequest(
                "session-1", "compaction-1", source, List.of(foreign), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ConversationCompactionCommit(
                "session-1", "compaction-1", 0, 1, "summary",
                List.of("missing"), source));
    }

    private static List<AgentMessageEntry> messages() {
        AgentMessageEntry first = CommonFixtures.userMessage();
        AgentMessageEntry second = new AgentMessageEntry("entry-2", "session-1", 1, 1,
                MessageRole.ASSISTANT, MessageType.TEXT, "answer", Map.of(), CommonFixtures.NOW);
        return List.of(first, second);
    }
}
