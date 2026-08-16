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
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.id.IdGenerator;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.skill.SkillProvider;
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
    final Deque<RuntimeException> modelFailures = new ArrayDeque<>();
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
    boolean failToolCallAuditReplace;
    boolean failPostCompressionAppend;

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
                    if (!modelFailures.isEmpty()) {
                        throw modelFailures.removeFirst();
                    }
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
                    public void commit(ConversationWriteBatch batch) {
                        List<AgentMessageEntry> entries =
                                batch.writes().stream()
                                        .filter(AppendConversationWrite.class::isInstance)
                                        .map(AppendConversationWrite.class::cast)
                                        .map(AppendConversationWrite::entry)
                                        .toList();
                        if (failPostCompressionAppend
                                && entries.stream()
                                        .anyMatch(entry -> "Hook补充".equals(entry.content()))) {
                            throw new IllegalStateException("post compression append failed");
                        }
                        if (failToolResultAppend
                                && entries.stream()
                                        .anyMatch(
                                                entry ->
                                                        entry.messageType()
                                                                == MessageType.TOOL_RESULT)) {
                            throw new IllegalStateException("tool result append failed");
                        }
                        if (failToolCallAuditReplace && containsResolvedArguments(batch)) {
                            throw new IllegalStateException("tool call audit replace failed");
                        }

                        List<AgentMessageEntry> nextConversation = new ArrayList<>(conversation);
                        ConversationSummary nextSummary = summary;
                        ConversationCompactionCommit nextCompactionCommit = compactionCommit;
                        for (ConversationWrite write : batch.writes()) {
                            switch (write) {
                                case AppendConversationWrite append -> {
                                    calls.add("conversation.append");
                                    if (nextConversation.stream()
                                            .noneMatch(
                                                    existing ->
                                                            existing.entryId()
                                                                    .equals(
                                                                            append.entry()
                                                                                    .entryId()))) {
                                        nextConversation.add(append.entry());
                                    }
                                }
                                case ReplaceConversationWrite replace -> {
                                    int index =
                                            editableIndex(
                                                    nextConversation,
                                                    nextSummary,
                                                    replace.targetEntryId());
                                    AgentMessageEntry target = nextConversation.get(index);
                                    nextConversation.set(
                                            index,
                                            new AgentMessageEntry(
                                                    target.entryId(),
                                                    target.sessionId(),
                                                    target.turnNo(),
                                                    target.sortNo(),
                                                    replace.role(),
                                                    replace.messageType(),
                                                    replace.content(),
                                                    replace.payload(),
                                                    target.createdTime()));
                                }
                                case RemoveConversationWrite remove ->
                                        nextConversation.remove(
                                                editableIndex(
                                                        nextConversation,
                                                        nextSummary,
                                                        remove.targetEntryId()));
                                case CompactConversationWrite compact -> {
                                    calls.add("conversation.compact");
                                    nextCompactionCommit = compact.commit();
                                    nextSummary = compact.commit().summary();
                                }
                            }
                        }
                        conversation.clear();
                        conversation.addAll(nextConversation);
                        summary = nextSummary;
                        compactionCommit = nextCompactionCommit;
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

                    private int editableIndex(
                            List<AgentMessageEntry> entries,
                            ConversationSummary currentSummary,
                            String targetEntryId) {
                        for (int index = 0; index < entries.size(); index++) {
                            AgentMessageEntry entry = entries.get(index);
                            if (!entry.entryId().equals(targetEntryId)) {
                                continue;
                            }
                            if (entry.messageType() == MessageType.SUMMARY
                                    || (currentSummary != null
                                            && entry.sortNo() >= currentSummary.sourceStartSortNo()
                                            && entry.sortNo()
                                                    <= currentSummary.sourceEndSortNo())) {
                                throw new IllegalArgumentException("消息不可编辑: " + targetEntryId);
                            }
                            return index;
                        }
                        throw new IllegalArgumentException("消息不存在: " + targetEntryId);
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
                new SkillProvider() {
                    @Override
                    public List<SkillMeta> loadSkills() {
                        return definition.enabledSkills().stream()
                                .map(name -> new SkillMeta(name, "测试 Skill"))
                                .toList();
                    }

                    @Override
                    public SkillDefinition loadSkill(String skillName) {
                        return new SkillDefinition(
                                new SkillMeta(skillName, "测试 Skill"), "instructions:" + skillName);
                    }

                    @Override
                    public String loadResource(String skillName, String resourcePath) {
                        return "resource:" + resourcePath;
                    }

                    @Override
                    public String loadResource(String path) {
                        return "resource:" + path;
                    }
                },
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

    private boolean containsResolvedArguments(ConversationWriteBatch batch) {
        for (ConversationWrite write : batch.writes()) {
            if (!(write instanceof ReplaceConversationWrite replace)
                    || !(replace.payload().get("toolCalls") instanceof List<?> calls)) {
                continue;
            }
            for (Object value : calls) {
                if (value instanceof Map<?, ?> call && call.containsKey("resolvedArguments")) {
                    return true;
                }
            }
        }
        return false;
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
