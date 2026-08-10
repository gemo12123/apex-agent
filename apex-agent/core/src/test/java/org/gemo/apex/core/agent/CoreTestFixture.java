package org.gemo.apex.core.agent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.agent.*;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelRequest;
import org.gemo.apex.common.model.ModelResponse;
import org.gemo.apex.common.model.ModelStreamChunk;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.id.IdGenerator;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.extension.tool.ToolProvider;
import org.gemo.apex.protocol.event.AgentMessage;

final class CoreTestFixture {
    final List<String> calls = new ArrayList<>();
    final List<AgentMessage> events = new ArrayList<>();
    final List<AgentMessageEntry> conversation = new ArrayList<>();
    final Map<String, SessionSnapshot> sessions = new HashMap<>();
    final Map<String, LifecycleHook<?, ?>> hooks = new HashMap<>();
    final Map<String, AgentTool> tools = new LinkedHashMap<>();
    final Deque<ModelResponse> modelResponses = new ArrayDeque<>();
    final List<ModelRequest> modelRequests = new ArrayList<>();
    final TestCancellationToken token = new TestCancellationToken();
    final AtomicInteger ids = new AtomicInteger();
    AgentDefinition definition;
    ConversationSummary summary;
    ConversationCompactionRequest compactionRequest;
    ConversationCompactionCommit compactionCommit;
    ConversationCompactionCheck compactionCheck;
    boolean compact;
    ToolAvailabilitySnapshot availability = new ToolAvailabilitySnapshot(Set.of(), List.of());
    int providerLoads;
    int modelCalls;
    int toolCalls;
    int windowLoads;
    RuntimeException modelFailure;
    boolean failSuspensionSave;
    int remainingSessionSaveFailures;
    boolean failToolResultAppend;

    CoreTestFixture() {
        definition = definition(Map.of(), Set.of(), Set.of());
    }

    AgentDefinition definition(
            Map<HookPoint, List<HookBinding>> bindings,
            Set<String> available,
            Set<String> defaults) {
        return definition(bindings, available, defaults, Set.of());
    }

    AgentDefinition definition(
            Map<HookPoint, List<HookBinding>> bindings,
            Set<String> available,
            Set<String> defaults,
            Set<String> enabledSkills) {
        return new AgentDefinition(
                DefinitionSchemaVersion.V1,
                new AgentMetadata("demo", "Demo", "测试 Agent"),
                new PromptDefinition("你是测试助手", 3),
                new MessageCompressionDefinition(true, 20),
                new ToolSetDefinition(available, defaults),
                enabledSkills,
                Map.of(),
                bindings);
    }

    AgentPorts ports() {
        return new AgentPorts(
                new AgentDefinitionProvider() {
                    @Override
                    public AgentDefinition load(String agentKey) {
                        providerLoads++;
                        calls.add("definition.load");
                        return definition;
                    }

                    @Override
                    public List<AgentMetadata> listAgents() {
                        return List.of(definition.metadata());
                    }
                },
                new ToolProvider() {
                    @Override
                    public List<AgentTool> loadTools(AgentDefinition ignored) {
                        calls.add("tools.load.new");
                        return List.copyOf(tools.values());
                    }
                },
                () -> availability,
                (point, name) -> hooks.get(name),
                (request, observer) -> {
                    modelCalls++;
                    calls.add("model");
                    modelRequests.add(request);
                    if (modelFailure != null) {
                        throw modelFailure;
                    }
                    ModelResponse response = modelResponses.removeFirst();
                    if (response.text() != null && !response.text().isEmpty()) {
                        observer.onChunk(
                                new ModelStreamChunk(response.text(), List.of(), Map.of(), true));
                    }
                    return response;
                },
                new SessionRepository() {
                    @Override
                    public Optional<SessionSnapshot> load(String sessionId) {
                        calls.add("session.load");
                        return Optional.ofNullable(sessions.get(sessionId));
                    }

                    @Override
                    public void save(SessionSnapshot snapshot) {
                        if (failSuspensionSave
                                && snapshot.status() == SessionStatus.HUMAN_IN_THE_LOOP) {
                            throw new IllegalStateException("suspension save failed");
                        }
                        if (remainingSessionSaveFailures > 0) {
                            remainingSessionSaveFailures--;
                            throw new IllegalStateException("session save failed");
                        }
                        calls.add("session.save");
                        sessions.put(snapshot.sessionId(), snapshot);
                    }
                },
                new ConversationRepository() {
                    @Override
                    public void append(List<AgentMessageEntry> entries) {
                        if (failToolResultAppend
                                && entries.stream()
                                        .anyMatch(
                                                entry ->
                                                        entry.messageType()
                                                                == MessageType.TOOL_RESULT)) {
                            throw new IllegalStateException("tool result append failed");
                        }
                        calls.add("conversation.append");
                        for (AgentMessageEntry entry : entries) {
                            if (conversation.stream()
                                    .noneMatch(
                                            existing ->
                                                    existing.entryId().equals(entry.entryId()))) {
                                conversation.add(entry);
                            }
                        }
                    }

                    @Override
                    public ConversationHistory load(ConversationQuery query) {
                        return new ConversationHistory(
                                query.sessionId(),
                                Optional.ofNullable(summary),
                                conversation.stream()
                                        .filter(item -> item.sessionId().equals(query.sessionId()))
                                        .sorted(Comparator.comparingLong(AgentMessageEntry::sortNo))
                                        .toList());
                    }

                    @Override
                    public void compact(ConversationCompactionCommit commit) {
                        calls.add("conversation.compact");
                        compactionCommit = commit;
                        summary = commit.summary();
                    }
                },
                request -> {
                    windowLoads++;
                    List<AgentMessageEntry> messages =
                            new ArrayList<>(
                                    conversation.stream()
                                            .filter(
                                                    item ->
                                                            item.sessionId()
                                                                    .equals(
                                                                            request.query()
                                                                                    .sessionId()))
                                            .filter(
                                                    item ->
                                                            summary == null
                                                                    || item.sortNo()
                                                                            < summary
                                                                                    .sourceStartSortNo()
                                                                    || item.sortNo()
                                                                            > summary
                                                                                    .sourceEndSortNo())
                                            .sorted(
                                                    Comparator.comparingLong(
                                                            AgentMessageEntry::sortNo))
                                            .toList());
                    if (summary != null) {
                        messages.add(summaryMessage(request.query().sessionId(), summary));
                        messages.sort(Comparator.comparingLong(AgentMessageEntry::sortNo));
                    }
                    return messages.isEmpty()
                            ? new ConversationWindow(
                                    request.query().sessionId(), List.of(), null, null)
                            : new ConversationWindow(
                                    request.query().sessionId(),
                                    summary,
                                    messages,
                                    messages.getFirst().sortNo(),
                                    messages.getLast().sortNo());
                },
                check -> {
                    calls.add("compact.check");
                    compactionCheck = check;
                    return compact;
                },
                request -> {
                    calls.add("compact.execute");
                    compactionRequest = request;
                    return new ConversationCompactionResult(
                            request.compactionId(), "摘要", request.retainedMessages(), Map.of());
                },
                () ->
                        definition.enabledSkills().stream()
                                .map(
                                        name ->
                                                new SkillDefinition(
                                                        name,
                                                        "测试 Skill",
                                                        "instructions:" + name,
                                                        Map.of()))
                                .toList(),
                message -> {
                    calls.add("event." + message.getClass().getSimpleName());
                    events.add(message);
                },
                token,
                new IdGenerator() {
                    private String next(String prefix) {
                        return prefix + ids.incrementAndGet();
                    }

                    @Override
                    public String newExecutionId() {
                        return next("execution-");
                    }

                    @Override
                    public String newEntryId() {
                        return next("entry-");
                    }

                    @Override
                    public String newInvocationId() {
                        return next("invocation-");
                    }

                    @Override
                    public String newConfirmationId() {
                        return next("confirmation-");
                    }

                    @Override
                    public String newSubSessionId() {
                        return next("sub-");
                    }

                    @Override
                    public String newCompactionId() {
                        return next("compaction-");
                    }
                },
                () -> Instant.parse("2026-08-01T00:00:00Z"),
                "直接输出最终结论且不再调用工具");
    }

    private AgentMessageEntry summaryMessage(String sessionId, ConversationSummary value) {
        return new AgentMessageEntry(
                "summary:" + value.compactionId(),
                sessionId,
                value.sourceTurnNo(),
                value.sourceEndSortNo(),
                org.gemo.apex.common.message.MessageRole.SYSTEM,
                MessageType.SUMMARY,
                value.content(),
                Map.of(
                        "sourceStartSortNo", value.sourceStartSortNo(),
                        "sourceEndSortNo", value.sourceEndSortNo()),
                value.updatedTime());
    }

    AgentTool tool(String name, ToolBehavior behavior) {
        AgentTool tool =
                new AgentTool() {
                    private final ToolDefinition definition =
                            new ToolDefinition(name, "测试工具", "{}", Map.of());

                    @Override
                    public ToolDefinition definition() {
                        return definition;
                    }

                    @Override
                    public ToolResult execute(
                            ToolCall call,
                            ToolExecutionContext context,
                            ToolExecutionObserver observer) {
                        toolCalls++;
                        calls.add("tool." + name);
                        return behavior.execute(call, context, observer);
                    }
                };
        tools.put(name, tool);
        return tool;
    }

    interface ToolBehavior {
        ToolResult execute(
                ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer);
    }

    static final class TestCancellationToken implements CancellationToken {
        private boolean cancelled;
        private final List<Runnable> commands = new ArrayList<>();

        @Override
        public boolean isCancellationRequested() {
            return cancelled;
        }

        @Override
        public void throwIfCancellationRequested() {
            if (cancelled) {
                throw new CancellationRequestedException();
            }
        }

        @Override
        public CancellationRegistration onCancel(Runnable command) {
            if (cancelled) {
                command.run();
            } else {
                commands.add(command);
            }
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
