package org.gemo.apex.runtime;

import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.execution.*;
import org.gemo.apex.common.message.*;
import org.gemo.apex.common.model.*;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.core.agent.AgentRunOutcome;
import org.gemo.apex.protocol.event.*;
import org.gemo.apex.runtime.api.*;
import org.gemo.apex.runtime.definition.*;
import org.gemo.apex.runtime.execution.*;
import org.gemo.apex.runtime.mcp.*;
import org.gemo.apex.runtime.model.springai.*;
import org.gemo.apex.runtime.repository.memory.*;
import org.gemo.apex.runtime.subagent.*;
import org.junit.jupiter.api.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeContractTest {
    /**
     * 无IoC默认Agent执行且End精确一次
     */
    @Test
    void executesDefaultAgentWithoutIoCAndPublishesEndExactlyOnce() {
        List<AgentMessage> events = new CopyOnWriteArrayList<>();
        try (var runtime = ApexAgentRuntime.builder().modelGateway((r, o) -> new ModelResponse("完成", List.of(), Map.of())).defaultEventPublisherFactory(d -> events::add).build()) {
            assertInstanceOf(AgentRunOutcome.Completed.class, runtime.newAgent(new AgentRequest("s", "default", "u", "q")).run());
            assertEquals(1, events.stream().filter(EndMessage.class::isInstance).count());
            assertTrue(events.stream().noneMatch(event -> event instanceof PlanDeclaredMessage || event instanceof PlanChangeMessage || event instanceof TaskThinkDeclaredMessage || event instanceof TaskThinkChangeMessage));
            assertEquals(0, runtime.activeExecutionCount());
        }
    }

    /**
     * provider在build不加载请求时加载
     */
    @Test
    void loadsProviderOnRequestRatherThanBuild() {
        var count = new AtomicInteger();
        var d = definition();
        var p = new org.gemo.apex.extension.definition.AgentDefinitionProvider() {
            public AgentDefinition load(String k) {
                count.incrementAndGet();
                return d;
            }

            public List<AgentMetadata> listAgents() {
                return List.of(d.metadata());
            }
        };
        try (var r = ApexAgentRuntime.builder().modelGateway((a, b) -> new ModelResponse("x", List.of(), Map.of())).agentDefinitionProvider(p).build()) {
            assertEquals(0, count.get());
            r.newAgent(new AgentRequest("s2", "default", "u", "q")).run();
            assertEquals(1, count.get());
        }
    }

    /**
     * builder互斥与同sessionLease
     */
    @Test
    void rejectsInvalidBuilderConfigurationAndEnforcesSameSessionLease() {
        assertThrows(RuntimeConfigurationException.class, () -> ApexAgentRuntime.builder().build());
        try (var r = ApexAgentRuntime.builder().modelGateway((a, b) -> new ModelResponse("x", List.of(), Map.of())).build()) {
            var first = r.newAgent(new AgentRequest("busy", "default", "u", "q"));
            assertThrows(SessionBusyException.class, () -> r.newAgent(new AgentRequest("busy", "default", "u", "q")));
            assertTrue(first.cancelBeforeStart());
            assertDoesNotThrow(() -> r.newAgent(new AgentRequest("busy", "default", "u", "q")).cancelBeforeStart());
        }
    }

    /**
     * 内存Conversation幂等与冲突
     */
    @Test
    void makesInMemoryConversationOperationsIdempotentAndDetectsConflicts() {
        var r = new InMemoryConversationRepository();
        var e = entry("e1", 0);
        r.append(List.of(e));
        r.append(List.of(e));
        assertEquals(1, r.load(new ConversationQuery("s")).size());
        assertThrows(IllegalStateException.class, () -> r.append(List.of(entry("e2", 0))));
        var c = new ConversationCompactionCommit("s", "c", 0, 0, "sum", List.of("e1"), List.of(e));
        r.compact(c);
        r.compact(c);
    }

    /**
     * 取消前后注册命令均精确一次
     */
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

    /**
     * SpringAiToolCall往返保留字段顺序
     */
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

    /**
     * mcp仅发送工具参数
     */
    @Test
    void sendsOnlyToolArgumentsToMcp() {
        List<Map<String, Object>> sent = new ArrayList<>();
        var t = new McpTransport() {
            public void connect() {
            }

            public List<ToolDefinition> listTools() {
                return List.of();
            }

            public McpCallHandle call(String n, Map<String, Object> a) {
                sent.add(a);
                return new McpCallHandle() {
                    public Map<String, Object> await() {
                        return Map.of("ok", true);
                    }

                    public void cancel() {
                    }
                };
            }

            public void close() {
            }
        };
        var a = new McpAgentToolAdapter(new ToolDefinition("mcp/x", "x", "{}", Map.of()), t);
        var s = new RuntimeCancellationSource();
        a.execute(new ToolCall("c", "mcp/x", 0, Map.of("value", 7), Map.of()), new ToolExecutionContext("s", 1, 1, "u", null, null, s.token(), Map.of("secret", "no")), new Observer(s));
        assertEquals(List.of(Map.of("value", 7)), sent);
    }

    /**
     * sse多行与边界
     */
    @Test
    void decodesMultilineSseEventsAtEventBoundary() {
        var d = new SseEventDecoder();
        d.accept("data: {");
        d.accept("data: }");
        assertEquals(List.of("{\n}"), d.accept(""));
    }

    /**
     * fileProvider初始化缓存且列出元数据
     */
    @Test
    void cachesFileProviderAtInitializationAndListsMetadata() throws Exception {
        Path f = Files.createTempFile("agents", ".yml");
        Files.writeString(f, """
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

    /**
     * owned资源反向关闭且borrowed不关闭
     */
    @Test
    void closesOwnedResourcesInReverseOrderButNotBorrowedResources() {
        List<String> closed = new ArrayList<>();
        AutoCloseable a = () -> closed.add("a"), b = () -> closed.add("b"), borrowed = () -> closed.add("borrowed");
        var r = ApexAgentRuntime.builder().modelGateway((x, o) -> new ModelResponse("ok", List.of(), Map.of())).ownedResource(a).ownedResource(b).borrowedResource(borrowed).build();
        r.close();
        r.close();
        assertEquals(List.of("b", "a"), closed);
        assertThrows(IllegalStateException.class, () -> r.newAgent(new AgentRequest("closed", "default", "u", "q")));
    }

    /**
     * 普通Skill重复激活幂等且资源限制为enabled
     */
    @Test
    void activatesRegularSkillIdempotentlyAndRestrictsResourcesToEnabledSkills() throws Exception {
        Path root = Files.createTempDirectory("skills"), dir = Files.createDirectory(root.resolve("pdf"));
        Files.writeString(dir.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n使用说明");
        Files.writeString(dir.resolve("guide.txt"), "资源");
        var loaded = new org.gemo.apex.runtime.skill.FileSkillProvider(root).loadSkills();
        var registry = new org.gemo.apex.runtime.skill.RuntimeSkillRegistry(loaded);
        assertEquals(Set.of("pdf"), registry.activate("pdf", Set.of("pdf"), Set.of()).activatedSkills());
        assertEquals(Set.of("pdf"), registry.activate("pdf", Set.of("pdf"), Set.of("pdf")).activatedSkills());
        assertEquals("资源", registry.read("pdf", "guide.txt", Set.of("pdf")));
        assertThrows(IllegalArgumentException.class, () -> registry.read("pdf", "guide.txt", Set.of()));
    }

    private static AgentDefinition definition() {
        return new AgentDefinition("1.0.0", new AgentMetadata("default", "默认", "测试"), new PromptDefinition("系统", 2), new MessageCompressionDefinition(false, 10), new ToolSetDefinition(Set.of(), Set.of()), Set.of(), Map.of(), Map.of());
    }

    private static AgentMessageEntry entry(String id, long sort) {
        return new AgentMessageEntry(id, "s", 1, sort, MessageRole.USER, MessageType.TEXT, "x", Map.of(), Instant.EPOCH);
    }

    private record Observer(RuntimeCancellationSource s) implements org.gemo.apex.extension.tool.ToolExecutionObserver {
        public void onEvent(AgentMessage e) {
        }

        public CancellationToken cancellationToken() {
            return s.token();
        }
    }
}
