package org.gemo.apex.runtime;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.message.*;
import org.gemo.apex.common.model.*;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.AgentRunOutcome;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.kit.hook.SkillActivationStateHook;
import org.gemo.apex.kit.tool.ActivateSkillTool;
import org.gemo.apex.protocol.event.*;
import org.gemo.apex.runtime.api.*;
import org.gemo.apex.runtime.conversation.DefaultConversationServices;
import org.gemo.apex.runtime.definition.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.model.springai.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.gemo.apex.runtime.skill.FileSkillProvider;
import org.gemo.apex.runtime.skill.RuntimeSkillRegistry;
import org.gemo.apex.runtime.subagent.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;

class RuntimeContractTest {
    /** 无IoC默认Agent执行且End精确一次 */
    @Test
    void executesDefaultAgentWithoutIoCAndPublishesEndExactlyOnce() {
        List<AgentMessage> events = new CopyOnWriteArrayList<>();
        try (var runtime =
                ApexAgentRuntime.builder()
                        .modelGateway((r, o) -> new ModelResponse("完成", List.of(), Map.of()))
                        .defaultEventPublisherFactory(d -> events::add)
                        .build()) {
            assertInstanceOf(
                    AgentRunOutcome.Completed.class,
                    runtime.newAgent(new AgentRequest("s", "default", "u", "q")).run());
            assertEquals(1, events.stream().filter(EndMessage.class::isInstance).count());
            assertTrue(
                    events.stream()
                            .noneMatch(
                                    event ->
                                            event instanceof PlanDeclaredMessage
                                                    || event instanceof PlanChangeMessage
                                                    || event instanceof TaskThinkDeclaredMessage
                                                    || event instanceof TaskThinkChangeMessage));
            assertEquals(0, runtime.activeExecutionCount());
        }
    }

    /** provider在build不加载请求时加载 */
    @Test
    void loadsProviderOnRequestRatherThanBuild() {
        var count = new AtomicInteger();
        var d = definition();
        var p =
                new AgentDefinitionProvider() {
                    public AgentDefinition load(String k) {
                        count.incrementAndGet();
                        return d;
                    }

                    public List<AgentMetadata> listAgents() {
                        return List.of(d.metadata());
                    }
                };
        try (var r =
                ApexAgentRuntime.builder()
                        .modelGateway((a, b) -> new ModelResponse("x", List.of(), Map.of()))
                        .agentDefinitionProvider(p)
                        .build()) {
            assertEquals(0, count.get());
            r.newAgent(new AgentRequest("s2", "default", "u", "q")).run();
            assertEquals(1, count.get());
        }
    }

    /** builder互斥与同sessionLease */
    @Test
    void rejectsInvalidBuilderConfigurationAndEnforcesSameSessionLease() {
        assertThrows(RuntimeConfigurationException.class, () -> ApexAgentRuntime.builder().build());
        try (var r =
                ApexAgentRuntime.builder()
                        .modelGateway((a, b) -> new ModelResponse("x", List.of(), Map.of()))
                        .build()) {
            var first = r.newAgent(new AgentRequest("busy", "default", "u", "q"));
            assertThrows(
                    SessionBusyException.class,
                    () -> r.newAgent(new AgentRequest("busy", "default", "u", "q")));
            assertTrue(first.cancelBeforeStart());
            assertDoesNotThrow(
                    () ->
                            r.newAgent(new AgentRequest("busy", "default", "u", "q"))
                                    .cancelBeforeStart());
        }
    }

    /** 内存Conversation幂等与冲突 */
    @Test
    void makesInMemoryConversationOperationsIdempotentAndDetectsConflicts() {
        var r = new InMemoryConversationRepository();
        var e = entry("e1", 0);
        r.append(List.of(e));
        r.append(List.of(e));
        assertEquals(1, r.load(new ConversationQuery("s")).messages().size());
        assertThrows(IllegalStateException.class, () -> r.append(List.of(entry("e2", 0))));
        var c =
                new ConversationCompactionCommit(
                        "s",
                        new ConversationSummary("c", "sum", 0, 0, 1, Instant.EPOCH),
                        List.of(),
                        List.of());
        r.compact(c);
        r.compact(c);
        assertEquals("sum", r.load(new ConversationQuery("s")).summary().orElseThrow().content());
    }

    /** 窗口按摘要范围替换原始消息且不预截断尾部 */
    @Test
    void assemblesSummaryMessageAndMessagesOutsideCoveredRange() {
        var repository = new InMemoryConversationRepository();
        AgentMessageEntry first = entry("e1", 0);
        AgentMessageEntry second = entry("e2", 1);
        AgentMessageEntry retained = entry("e3", 2);
        repository.append(List.of(first, second, retained));
        repository.compact(
                new ConversationCompactionCommit(
                        "s",
                        new ConversationSummary("c", "累计摘要", 0, 1, 1, Instant.EPOCH),
                        List.of("e3"),
                        List.of(retained)));

        ConversationWindow window =
                DefaultConversationServices.window(repository)
                        .prepare(new ConversationWindowRequest(new ConversationQuery("s")));

        assertEquals(
                List.of(MessageType.SUMMARY, MessageType.TEXT),
                window.messages().stream().map(AgentMessageEntry::messageType).toList());
        assertEquals(
                List.of("累计摘要", retained.content()),
                window.messages().stream().map(AgentMessageEntry::content).toList());
        assertEquals(3, repository.load(new ConversationQuery("s")).messages().size());
    }

    /** 默认策略对消息数及两个可选容量阈值执行OR判断 */
    @Test
    void evaluatesConfiguredCompactionThresholdsIndependently() {
        var policy = DefaultConversationServices.policy();
        var trigger = new ConversationCompactionTrigger("s", 1, 1, "MODEL_CALL");
        assertTrue(policy.shouldCompact(check(1, 10, 10L, null, 10, 100, trigger)));
        assertTrue(policy.shouldCompact(check(1, 10, null, 100L, 10, 100, trigger)));
        assertFalse(policy.shouldCompact(check(1, 10, null, null, 10, 100, trigger)));
        assertTrue(policy.shouldCompact(check(2, 1, null, null, 2, 100, trigger)));
    }

    private ConversationCompactionCheck check(
            int messageCount,
            int messageThreshold,
            Long tokenThreshold,
            Long characterHardLimit,
            long totalTokens,
            long totalCharacters,
            ConversationCompactionTrigger trigger) {
        return new ConversationCompactionCheck(
                java.util.stream.IntStream.range(0, messageCount)
                        .mapToObj(index -> entry("check-" + index, index))
                        .toList(),
                totalTokens,
                totalCharacters,
                0,
                0,
                0,
                0,
                totalTokens,
                totalCharacters,
                messageThreshold,
                tokenThreshold,
                characterHardLimit,
                0,
                trigger);
    }

    /** 取消前后注册命令均精确一次 */
    @Test
    void invokesCancellationCallbacksExactlyOnceBeforeAndAfterCancellation() {
        var s = new RuntimeCancellationSource();
        var a = new AtomicInteger();
        s.token().onCancel(a::incrementAndGet);
        assertTrue(s.cancel());
        assertFalse(s.cancel());
        s.token().onCancel(a::incrementAndGet);
        assertEquals(2, a.get());
    }

    /** SpringAiToolCall往返保留字段顺序 */
    @Test
    void preservesSpringAiToolCallFieldsAndOrdinalOnRoundTrip() {
        var m = new SpringAiMessageMapper();
        var c = new ToolCall("id", "tool", 0, Map.of("x", 1), Map.of());
        var out = m.fromSpring(m.toSpring(c), 0);
        assertEquals(c.toolCallId(), out.toolCallId());
        assertEquals(c.name(), out.name());
        assertEquals(c.arguments(), out.arguments());
        assertEquals(0, out.ordinal());
    }

    @Test
    void mapsAssistantToolCallsFromConversationPayload() {
        var message =
                new AgentMessageEntry(
                        "entry",
                        "session",
                        1,
                        0,
                        MessageRole.ASSISTANT,
                        MessageType.TOOL_CALLS,
                        "正在查询",
                        Map.of(
                                "toolCalls",
                                List.of(
                                        Map.of(
                                                "toolCallId",
                                                "call-1",
                                                "name",
                                                "weather",
                                                "arguments",
                                                Map.of("city", "上海")))),
                        Instant.EPOCH);

        var mapped =
                assertInstanceOf(
                        AssistantMessage.class, new SpringAiMessageMapper().toSpring(message));

        assertEquals("正在查询", mapped.getText());
        assertEquals(1, mapped.getToolCalls().size());
        var call = mapped.getToolCalls().getFirst();
        assertEquals("call-1", call.id());
        assertEquals("function", call.type());
        assertEquals("weather", call.name());
        assertEquals("{\"city\":\"上海\"}", call.arguments());
    }

    /** sse多行与边界 */
    @Test
    void decodesMultilineSseEventsAtEventBoundary() {
        var d = new SseEventDecoder();
        d.accept("data: {");
        d.accept("data: }");
        assertEquals(List.of("{\n}"), d.accept(""));
    }

    /** fileProvider初始化缓存且列出元数据 */
    @Test
    void cachesFileProviderAtInitializationAndListsMetadata() throws Exception {
        Path f = Files.createTempFile("agents", ".yml");
        Files.writeString(
                f,
                """
                agents:
                  default:
                    schemaVersion: 1.0.0
                    metadata: {agentKey: default, name: 默认, description: 测试}
                    prompt: {systemPrompt: 系统, maxIterations: 2}
                    messageCompression: {enabled: false, maxMessages: 10}
                    tools: {availableTools: [], defaultEnabledTools: []}
                    enabledSkills: []
                    subAgents: {}
                    hooks: {}
                """);
        var p = new FileAgentDefinitionProvider(f.toUri());
        Files.writeString(f, "agents: {}");
        assertEquals("系统", p.load("default").prompt().systemPrompt());
        assertEquals(1, p.listAgents().size());
    }

    /** owned资源反向关闭且borrowed不关闭 */
    @Test
    void closesOwnedResourcesInReverseOrderButNotBorrowedResources() {
        List<String> closed = new ArrayList<>();
        AutoCloseable a = () -> closed.add("a"),
                b = () -> closed.add("b"),
                borrowed = () -> closed.add("borrowed");
        var r =
                ApexAgentRuntime.builder()
                        .modelGateway((x, o) -> new ModelResponse("ok", List.of(), Map.of()))
                        .ownedResource(a)
                        .ownedResource(b)
                        .borrowedResource(borrowed)
                        .build();
        r.close();
        r.close();
        assertEquals(List.of("b", "a"), closed);
        assertThrows(
                IllegalStateException.class,
                () -> r.newAgent(new AgentRequest("closed", "default", "u", "q")));
    }

    /** 普通Skill注册表缓存定义且资源限制为enabled */
    @Test
    void cachesRegularSkillsAndRestrictsResourcesToEnabledSkills() throws Exception {
        Path root = Files.createTempDirectory("skills"),
                dir = Files.createDirectory(root.resolve("pdf"));
        Files.writeString(dir.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n使用说明");
        Files.writeString(dir.resolve("guide.txt"), "资源");
        var loaded = new FileSkillProvider(root).loadSkills();
        var registry = new RuntimeSkillRegistry(loaded);
        assertEquals(
                List.of("pdf"), registry.loadSkills().stream().map(SkillDefinition::name).toList());
        assertEquals("资源", registry.read("pdf", "guide.txt", Set.of("pdf")));
        assertThrows(
                IllegalArgumentException.class, () -> registry.read("pdf", "guide.txt", Set.of()));
    }

    /** Skill激活工具不再隐式注册，显式组合Kit工具和Hook后才更新会话状态 */
    @Test
    void requiresExplicitSkillToolRegistrationAndSupportsKitComposition() {
        SkillDefinition skill = new SkillDefinition("pdf", "PDF", "使用 PDF 指令", Map.of());
        AgentDefinition activationDefinition =
                new AgentDefinition(
                        "1.0.0",
                        new AgentMetadata("default", "默认", "测试"),
                        new PromptDefinition("系统", 2),
                        new MessageCompressionDefinition(false, 10),
                        new ToolSetDefinition(
                                Set.of(ActivateSkillTool.NAME), Set.of(ActivateSkillTool.NAME)),
                        Set.of("pdf"),
                        Map.of(),
                        Map.of(
                                HookPoint.POST_TOOL_CALL,
                                List.of(
                                        new HookBinding(
                                                "skill-state",
                                                SkillActivationStateHook.REGISTRATION_NAME,
                                                0,
                                                true,
                                                List.of(ActivateSkillTool.NAME),
                                                Map.of()))));

        try (var missing =
                ApexAgentRuntime.builder()
                        .modelGateway((request, observer) -> null)
                        .agentDefinition(activationDefinition)
                        .registerSkill(skill)
                        .build()) {
            assertThrows(
                    AgentPreparationException.class,
                    () -> missing.newAgent(new AgentRequest("missing", "default", "u", "q")));
        }

        Deque<ModelResponse> responses =
                new ArrayDeque<>(
                        List.of(
                                new ModelResponse(
                                        "",
                                        List.of(
                                                new ToolCall(
                                                        "activate-1",
                                                        ActivateSkillTool.NAME,
                                                        0,
                                                        Map.of(
                                                                ActivateSkillTool.COMMAND_ARGUMENT,
                                                                "pdf"),
                                                        Map.of())),
                                        Map.of()),
                                new ModelResponse("完成", List.of(), Map.of())));
        InMemorySessionRepository sessions = new InMemorySessionRepository();
        try (var runtime =
                ApexAgentRuntime.builder()
                        .modelGateway((request, observer) -> responses.removeFirst())
                        .agentDefinition(activationDefinition)
                        .sessionRepository(sessions)
                        .registerSkill(skill)
                        .registerTool(new ActivateSkillTool(() -> List.of(skill)))
                        .registerHook(
                                SkillActivationStateHook.REGISTRATION_NAME,
                                new SkillActivationStateHook())
                        .build()) {
            ApexAgentExecution execution =
                    runtime.newAgent(new AgentRequest("explicit", "default", "u", "q"));

            assertInstanceOf(AgentRunOutcome.Completed.class, execution.run());
            assertEquals(Set.of("pdf"), sessions.load("explicit").orElseThrow().activatedSkills());
        }
    }

    private static AgentDefinition definition() {
        return new AgentDefinition(
                "1.0.0",
                new AgentMetadata("default", "默认", "测试"),
                new PromptDefinition("系统", 2),
                new MessageCompressionDefinition(false, 10),
                new ToolSetDefinition(Set.of(), Set.of()),
                Set.of(),
                Map.of(),
                Map.of());
    }

    private static AgentMessageEntry entry(String id, long sort) {
        return new AgentMessageEntry(
                id, "s", 1, sort, MessageRole.USER, MessageType.TEXT, "x", Map.of(), Instant.EPOCH);
    }

    private record Observer(RuntimeCancellationSource s) implements ToolExecutionObserver {
        public void onEvent(AgentMessage e) {}

        public CancellationToken cancellationToken() {
            return s.token();
        }
    }
}
