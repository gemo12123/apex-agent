package org.gemo.apex.common;

import org.gemo.apex.common.agent.ToolSetDefinition;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.snapshot.ToolExecutionSnapshot;
import org.gemo.apex.common.skill.SkillSetDefinition;
import org.gemo.apex.common.skill.SkillActivationResult;
import org.gemo.apex.common.tool.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DomainModelContractTest {
    /**
     * toolCall和ToolResult应无损往返并保持顺序
     */
    @Test
    void toolCallAndToolResultRoundTripLosslesslyAndPreserveOrder() {
        ToolCall first = CommonFixtures.toolCall();
        ToolCall second = new ToolCall("call-2", "fetch", 1, Map.of("url", "https://example.test"), Map.of());
        ModelResponse response = new ModelResponse("", List.of(first, second), Map.of("vendor", "test"));

        ModelResponse copy = JsonUtils.fromJson(JsonUtils.toJson(response), ModelResponse.class);

        assertEquals(response, copy);
        assertEquals(List.of("call-1", "call-2"), copy.toolCalls().stream().map(ToolCall::toolCallId).toList());
    }

    /**
     * 模型响应应拒绝不连续顺序和重复ID
     */
    @Test
    void rejectsDiscontinuousOrdinalsAndDuplicateIdsInModelResponse() {
        ToolCall badOrdinal = new ToolCall("call-1", "search", 1, Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new ModelResponse(null, List.of(badOrdinal), Map.of()));
        ToolCall duplicate = new ToolCall("call-1", "fetch", 1, Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class,
                () -> new ModelResponse(null, List.of(CommonFixtures.toolCall(), duplicate), Map.of()));
    }

    /**
     * 工具和Skill默认集合必须是可用集合子集
     */
    @Test
    void ensuresToolAndSkillDefaultSetsAreSubsetsOfAvailableSets() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolSetDefinition(Set.of("a"), Set.of("b")));
        assertThrows(IllegalArgumentException.class,
                () -> new SkillSetDefinition(Set.of("a"), Set.of("b")));
    }

    /**
     * Skill激活结果应保留指令并冻结激活集合
     */
    @Test
    void skillActivationResultPreservesInstructionsAndFreezesActivatedSkills() {
        LinkedHashSet<String> activatedSkills = new LinkedHashSet<>(Set.of("writing"));
        SkillActivationResult result = new SkillActivationResult("使用写作规范", activatedSkills);

        activatedSkills.add("other");

        assertEquals("使用写作规范", result.instructions());
        assertEquals(Set.of("writing"), result.activatedSkills());
        assertThrows(UnsupportedOperationException.class,
                () -> result.activatedSkills().add("another"));
    }

    /**
     * 输入集合和嵌套Map不能修改领域对象
     */
    @Test
    void doesNotAllowInputCollectionsOrNestedMapsToMutateDomainObjects() {
        List<Object> nested = new ArrayList<>(List.of("before"));
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("nested", nested);
        ToolCall call = new ToolCall("call", "tool", 0, arguments, Map.of());

        nested.add("after");
        arguments.put("other", true);

        assertEquals(List.of("before"), call.arguments().get("nested"));
        assertFalse(call.arguments().containsKey("other"));
        assertThrows(UnsupportedOperationException.class,
                () -> ((List<Object>) call.arguments().get("nested")).add("x"));
    }

    /**
     * availability只允许精确名称或同来源稳定前缀匹配
     */
    @Test
    void availabilityAllowsOnlyExactNameOrStableSameSourcePrefixMatches() {
        UnavailableToolSource source = new UnavailableToolSource(ToolOrigin.MCP, "github", "mcp.github.",
                "INIT_FAILED", Instant.parse("2026-08-01T00:00:00Z"));
        ToolAvailabilitySnapshot snapshot = new ToolAvailabilitySnapshot(Set.of("local.exact"), List.of(source));

        assertTrue(snapshot.isUnavailable("local.exact", ToolOrigin.LOCAL, "local"));
        assertTrue(snapshot.isUnavailable("mcp.github.search", ToolOrigin.MCP, "github"));
        assertFalse(snapshot.isUnavailable("prefix-mcp.github.search", ToolOrigin.MCP, "github"));
        assertFalse(snapshot.isUnavailable("mcp.github.search", ToolOrigin.MCP, "other"));
        assertFalse(snapshot.isUnavailable("mcp.github.search", ToolOrigin.SUB_AGENT, "github"));
    }

    /**
     * 四层状态均包含Cancelled并可序列化
     */
    @Test
    void allFourStateLevelsIncludeCancelledAndAreSerializable() {
        assertEquals(SessionStatus.CANCELLED, roundTrip(SessionStatus.CANCELLED, SessionStatus.class));
        assertEquals(TurnStatus.CANCELLED, roundTrip(TurnStatus.CANCELLED, TurnStatus.class));
        assertEquals(IterationStatus.CANCELLED, roundTrip(IterationStatus.CANCELLED, IterationStatus.class));
        assertEquals(ToolExecutionStatus.CANCELLED,
                roundTrip(ToolExecutionStatus.CANCELLED, ToolExecutionStatus.class));
        ToolExecutionSnapshot snapshot = new ToolExecutionSnapshot(CommonFixtures.toolCall(),
                ToolExecutionStatus.CANCELLED, null);
        assertEquals(snapshot, roundTrip(snapshot, ToolExecutionSnapshot.class));
    }

    /**
     * metadata应立即拒绝取消token和不可序列化对象
     */
    @Test
    void metadataRejectsCancellationTokensAndNonSerializableObjectsImmediately() {
        CancellationToken token = new CancellationToken() {
            @Override public boolean isCancellationRequested() { return false; }
            @Override public CancellationRegistration onCancel(Runnable command) { return () -> { }; }
        };
        assertThrows(IllegalArgumentException.class,
                () -> new ToolCall("call", "tool", 0, Map.of(), Map.of("token", token)));
        assertThrows(RuntimeException.class,
                () -> new ToolCall("call", "tool", 0, Map.of(), Map.of("bad", new Object())));
    }

    private static <T> T roundTrip(T value, Class<T> type) {
        return JsonUtils.fromJson(JsonUtils.toJson(value), type);
    }
}
