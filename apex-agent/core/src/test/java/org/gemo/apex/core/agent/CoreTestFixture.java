package org.gemo.apex.core.agent;

import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.protocol.event.AgentMessage;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

final class CoreTestFixture {
    final List<String> calls = new ArrayList<>();
    final List<AgentMessage> events = new ArrayList<>();
    final List<AgentMessageEntry> conversation = new ArrayList<>();
    final Map<String, SessionSnapshot> sessions = new HashMap<>();
    final Map<String, LifecycleHook<?, ?>> hooks = new HashMap<>();
    final Map<String, AgentTool> tools = new LinkedHashMap<>();
    final Deque<ModelResponse> modelResponses = new ArrayDeque<>();
    final List<org.gemo.apex.common.model.ModelRequest> modelRequests = new ArrayList<>();
    final TestCancellationToken token = new TestCancellationToken();
    final AtomicInteger ids = new AtomicInteger();
    AgentDefinition definition;
    boolean compact;
    ToolAvailabilitySnapshot availability = new ToolAvailabilitySnapshot(Set.of(), List.of());
    int providerLoads;
    int modelCalls;
    int toolCalls;
    RuntimeException modelFailure;

    CoreTestFixture() {
        definition = definition(Map.of(), Set.of(), Set.of());
    }

    AgentDefinition definition(Map<HookPoint, List<HookBinding>> bindings,
                               Set<String> available, Set<String> defaults) {
        return new AgentDefinition(DefinitionSchemaVersion.V1,
                new AgentMetadata("demo", "Demo", "测试 Agent"),
                new PromptDefinition("你是测试助手", 30), new MessageCompressionDefinition(true, 20),
                new ToolSetDefinition(available, defaults), Set.of(), Map.of(), bindings);
    }

    AgentPorts ports() {
        return new AgentPorts(
                new org.gemo.apex.extension.definition.AgentDefinitionProvider() {
                    @Override public AgentDefinition load(String agentKey) {
                        providerLoads++; calls.add("definition.load"); return definition;
                    }
                    @Override public List<AgentMetadata> listAgents() { return List.of(definition.metadata()); }
                },
                new org.gemo.apex.extension.tool.ToolProvider() {
                    @Override public List<AgentTool> loadTools(AgentDefinition ignored) {
                        calls.add("tools.load.new"); return List.copyOf(tools.values());
                    }
                    @Override public List<AgentTool> loadTools(AgentDefinitionRecoverySnapshot ignored) {
                        calls.add("tools.load.resume"); return List.copyOf(tools.values());
                    }
                }, () -> availability,
                (point, name) -> hooks.get(name),
                (request, observer) -> {
                    modelCalls++; calls.add("model");
                    modelRequests.add(request);
                    if (modelFailure != null) throw modelFailure;
                    ModelResponse response = modelResponses.removeFirst();
                    if (response.text() != null && !response.text().isEmpty()) {
                        observer.onChunk(new org.gemo.apex.common.model.ModelStreamChunk(
                                response.text(), List.of(), Map.of(), true));
                    }
                    return response;
                },
                new org.gemo.apex.extension.repository.SessionRepository() {
                    @Override public Optional<SessionSnapshot> load(String sessionId) {
                        calls.add("session.load"); return Optional.ofNullable(sessions.get(sessionId));
                    }
                    @Override public void save(SessionSnapshot snapshot) {
                        calls.add("session.save"); sessions.put(snapshot.sessionId(), snapshot);
                    }
                },
                new org.gemo.apex.extension.repository.ConversationRepository() {
                    @Override public void append(List<AgentMessageEntry> entries) {
                        calls.add("conversation.append"); conversation.addAll(entries);
                    }
                    @Override public List<AgentMessageEntry> load(ConversationQuery query) {
                        return conversation.stream().filter(item -> item.sessionId().equals(query.sessionId()))
                                .sorted(Comparator.comparingLong(AgentMessageEntry::sortNo)).toList();
                    }
                    @Override public void compact(ConversationCompactionCommit commit) {
                        calls.add("conversation.compact");
                    }
                },
                request -> {
                    List<AgentMessageEntry> messages = conversation.stream()
                            .filter(item -> item.sessionId().equals(request.query().sessionId()))
                            .sorted(Comparator.comparingLong(AgentMessageEntry::sortNo)).toList();
                    return messages.isEmpty()
                            ? new ConversationWindow(request.query().sessionId(), List.of(), null, null)
                            : new ConversationWindow(request.query().sessionId(), messages,
                            messages.getFirst().sortNo(), messages.getLast().sortNo());
                },
                check -> { calls.add("compact.check"); return compact; },
                request -> {
                    calls.add("compact.execute");
                    return new ConversationCompactionResult(request.compactionId(), "摘要",
                            request.retainedMessages(), Map.of());
                },
                () -> List.of(),
                (name, enabled, activated) -> new org.gemo.apex.common.skill.SkillActivationResult("instructions", activated),
                message -> { calls.add("event." + message.getClass().getSimpleName()); events.add(message); },
                token,
                new org.gemo.apex.extension.id.IdGenerator() {
                    private String next(String prefix) { return prefix + ids.incrementAndGet(); }
                    @Override public String newExecutionId() { return next("execution-"); }
                    @Override public String newEntryId() { return next("entry-"); }
                    @Override public String newInvocationId() { return next("invocation-"); }
                    @Override public String newConfirmationId() { return next("confirmation-"); }
                    @Override public String newSubSessionId() { return next("sub-"); }
                    @Override public String newCompactionId() { return next("compaction-"); }
                },
                () -> Instant.parse("2026-08-01T00:00:00Z"),
                3, 100_000, "直接输出最终结论且不再调用工具");
    }

    AgentTool tool(String name, ToolBehavior behavior) {
        AgentTool tool = new AgentTool() {
            private final ToolDefinition definition = new ToolDefinition(name, "测试工具", "{}", Map.of());
            @Override public ToolDefinition definition() { return definition; }
            @Override public ToolResult execute(ToolCall call, ToolExecutionContext context,
                                                org.gemo.apex.extension.tool.ToolExecutionObserver observer) {
                toolCalls++; calls.add("tool." + name); return behavior.execute(call, context, observer);
            }
        };
        tools.put(name, tool);
        return tool;
    }

    interface ToolBehavior {
        ToolResult execute(ToolCall call, ToolExecutionContext context,
                           org.gemo.apex.extension.tool.ToolExecutionObserver observer);
    }

    static final class TestCancellationToken implements CancellationToken {
        private boolean cancelled;
        private final List<Runnable> commands = new ArrayList<>();
        @Override public boolean isCancellationRequested() { return cancelled; }
        @Override public void throwIfCancellationRequested() {
            if (cancelled) throw new CancellationRequestedException();
        }
        @Override public CancellationRegistration onCancel(Runnable command) {
            if (cancelled) command.run(); else commands.add(command);
            return () -> commands.remove(command);
        }
        void cancel() {
            if (!cancelled) {
                cancelled = true;
                List.copyOf(commands).forEach(Runnable::run);
            }
        }
    }
}
