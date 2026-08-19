package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.agent.PrefixDeveloperMessage;
import org.gemo.apex.common.agent.ToolSetDefinition;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.hook.operation.SkillActivationDelta;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.skill.SkillResource;
import org.gemo.apex.common.tool.*;
import org.junit.jupiter.api.Test;

class DomainModelContractTest {
    /** 前置开发者消息只允许系统和用户文本，模型请求兼容旧构造器并冻结列表 */
    @Test
    void validatesPrefixDeveloperMessagesAndKeepsLegacyModelRequestConstructor() {
        PrefixDeveloperMessage system = new PrefixDeveloperMessage(MessageRole.SYSTEM, "系统前置");
        List<PrefixDeveloperMessage> source = new ArrayList<>(List.of(system));
        ModelRequest request =
                new ModelRequest(
                        "系统",
                        source,
                        CommonFixtures.modelRequest().messages(),
                        List.of(),
                        Map.of());

        source.add(new PrefixDeveloperMessage(MessageRole.USER, "用户前置"));

        assertEquals(List.of(system), request.prefixDeveloperMessages());
        assertTrue(CommonFixtures.modelRequest().prefixDeveloperMessages().isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> request.prefixDeveloperMessages().add(system));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrefixDeveloperMessage(MessageRole.ASSISTANT, "不允许"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrefixDeveloperMessage(MessageRole.TOOL, "不允许"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PrefixDeveloperMessage(MessageRole.SYSTEM, " "));
    }

    /** toolCall和ToolResult应无损往返并保持顺序 */
    @Test
    void toolCallAndToolResultRoundTripLosslesslyAndPreserveOrder() {
        ToolCall first = CommonFixtures.toolCall();
        ToolCall second =
                new ToolCall("call-2", "fetch", 1, Map.of("url", "https://example.test"), Map.of());
        ModelResponse response =
                new ModelResponse("", List.of(first, second), Map.of("vendor", "test"));

        ModelResponse copy = JsonUtils.fromJson(JsonUtils.toJson(response), ModelResponse.class);

        assertEquals(response, copy);
        assertEquals(
                List.of("call-1", "call-2"),
                copy.toolCalls().stream().map(ToolCall::toolCallId).toList());
    }

    /** 模型响应应拒绝不连续顺序和重复ID */
    @Test
    void rejectsDiscontinuousOrdinalsAndDuplicateIdsInModelResponse() {
        ToolCall badOrdinal = new ToolCall("call-1", "search", 1, Map.of(), Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelResponse(null, List.of(badOrdinal), Map.of()));
        ToolCall duplicate = new ToolCall("call-1", "fetch", 1, Map.of(), Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ModelResponse(
                                null, List.of(CommonFixtures.toolCall(), duplicate), Map.of()));
    }

    /** 工具默认集合必须是可用集合子集 */
    @Test
    void ensuresToolDefaultSetIsSubsetOfAvailableSet() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolSetDefinition(Set.of("a"), Set.of("b")));
    }

    /** Skill激活变更应冻结集合并拒绝冲突名称 */
    @Test
    void skillActivationDeltaFreezesSetsAndRejectsOverlappingNames() {
        LinkedHashSet<String> activatedSkills = new LinkedHashSet<>(Set.of("writing"));
        SkillActivationDelta delta = new SkillActivationDelta(activatedSkills, Set.of());

        activatedSkills.add("other");

        assertEquals(Set.of("writing"), delta.activate());
        assertThrows(UnsupportedOperationException.class, () -> delta.activate().add("another"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SkillActivationDelta(Set.of("writing"), Set.of("writing")));
    }

    /** 输入集合和嵌套Map不能修改领域对象 */
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
        assertThrows(
                UnsupportedOperationException.class,
                () -> ((List<Object>) call.arguments().get("nested")).add("x"));
    }

    /** availability只允许精确名称或同来源稳定前缀匹配 */
    @Test
    void availabilityAllowsOnlyExactNameOrStableSameSourcePrefixMatches() {
        UnavailableToolSource source =
                new UnavailableToolSource(
                        ToolOrigin.SUB_AGENT,
                        "weather-agent",
                        "subagent.weather.",
                        "INIT_FAILED",
                        Instant.parse("2026-08-01T00:00:00Z"));
        ToolAvailabilitySnapshot snapshot =
                new ToolAvailabilitySnapshot(Set.of("local.exact"), List.of(source));

        assertTrue(snapshot.isUnavailable("local.exact", ToolOrigin.LOCAL, "local"));
        assertTrue(
                snapshot.isUnavailable(
                        "subagent.weather.search", ToolOrigin.SUB_AGENT, "weather-agent"));
        assertFalse(
                snapshot.isUnavailable(
                        "prefix-subagent.weather.search", ToolOrigin.SUB_AGENT, "weather-agent"));
        assertFalse(
                snapshot.isUnavailable(
                        "subagent.weather.search", ToolOrigin.SUB_AGENT, "other-agent"));
        assertFalse(
                snapshot.isUnavailable(
                        "subagent.weather.search", ToolOrigin.LOCAL, "weather-agent"));
    }

    /** 三层执行状态均包含Cancelled并可序列化 */
    @Test
    void allThreeStateLevelsIncludeCancelledAndAreSerializable() {
        assertEquals(
                SessionStatus.CANCELLED, roundTrip(SessionStatus.CANCELLED, SessionStatus.class));
        assertEquals(TurnStatus.CANCELLED, roundTrip(TurnStatus.CANCELLED, TurnStatus.class));
        assertEquals(
                IterationStatus.CANCELLED,
                roundTrip(IterationStatus.CANCELLED, IterationStatus.class));
    }

    /** metadata应立即拒绝取消token和不可序列化对象 */
    @Test
    void metadataRejectsCancellationTokensAndNonSerializableObjectsImmediately() {
        CancellationToken token =
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
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolCall("call", "tool", 0, Map.of(), Map.of("token", token)));
        assertThrows(
                RuntimeException.class,
                () -> new ToolCall("call", "tool", 0, Map.of(), Map.of("bad", new Object())));
    }

    /** SkillDefinition组合并保留可扩展的SkillMeta实例 */
    @Test
    void keepsExtendedSkillMetadataInsideLoadedDefinition() {
        class ExtendedSkillMeta extends SkillMeta {
            private final String version;

            ExtendedSkillMeta(String name, String description, String version) {
                super(name, description);
                this.version = version;
            }
        }
        ExtendedSkillMeta meta = new ExtendedSkillMeta("pdf", "PDF", "2");

        SkillDefinition definition = new SkillDefinition(meta, "使用说明");

        assertSame(meta, definition.meta());
        assertEquals("2", ((ExtendedSkillMeta) definition.meta()).version);
        assertThrows(IllegalArgumentException.class, () -> new SkillMeta(" ", "PDF"));
        assertThrows(IllegalArgumentException.class, () -> new SkillDefinition(meta, " "));
    }

    /** Skill正文和资源内容支持一次性延迟缓存，资源索引保持不可变 */
    @Test
    void lazilyCachesSkillInstructionsAndResourceContent() {
        SkillMeta meta = new SkillMeta("pdf", "PDF");
        SkillResource resource =
                new SkillResource("references/guide.md", "guide.md", "text/markdown");
        SkillDefinition definition = new SkillDefinition(meta, Map.of(resource.path(), resource));

        assertNull(definition.instructions());
        assertNull(resource.content());
        assertSame(resource, definition.resources().get("references/guide.md"));

        definition.cacheInstructions("首次正文");
        definition.cacheInstructions("后续正文");
        resource.cacheContent("首次内容");
        resource.cacheContent("后续内容");

        assertEquals("首次正文", definition.instructions());
        assertEquals("首次内容", resource.content());
        assertThrows(
                UnsupportedOperationException.class,
                () -> definition.resources().put("other.txt", resource));
    }

    private static <T> T roundTrip(T value, Class<T> type) {
        return JsonUtils.fromJson(JsonUtils.toJson(value), type);
    }
}
