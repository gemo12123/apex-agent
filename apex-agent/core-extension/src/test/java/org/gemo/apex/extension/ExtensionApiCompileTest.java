package org.gemo.apex.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.gemo.apex.common.agent.AgentDefinition;
import org.gemo.apex.common.agent.AgentMetadata;
import org.gemo.apex.common.conversation.*;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.HookContextView;
import org.gemo.apex.common.hook.result.LifecycleHookResult;
import org.gemo.apex.common.message.AgentMessageEntry;
import org.gemo.apex.common.message.MessageRole;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.model.ModelStreamChunk;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.common.tool.CancellationRegistration;
import org.gemo.apex.common.tool.CancellationToken;
import org.gemo.apex.common.tool.ToolAvailabilitySnapshot;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.conversation.ConversationCompactionPolicy;
import org.gemo.apex.extension.conversation.ConversationCompactor;
import org.gemo.apex.extension.conversation.ConversationWindowManager;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.event.AgentEventPublisher;
import org.gemo.apex.extension.event.AgentEventPublisherFactory;
import org.gemo.apex.extension.hook.HookResolver;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.id.IdGenerator;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.model.ModelStreamObserver;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.time.TimeProvider;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolAvailabilityProvider;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.extension.tool.ToolProvider;
import org.gemo.apex.protocol.event.AgentMessage;
import org.junit.jupiter.api.Test;

class ExtensionApiCompileTest {

    /** 全部端口可由纯JDKFake实现且无需Spring */
    @Test
    void allPortsCanBeImplementedByPureJdkFakesWithoutSpring() {
        CancellationToken token = new TestCancellationToken();
        ModelStreamObserver modelObserver =
                new ModelStreamObserver() {
                    @Override
                    public void onChunk(ModelStreamChunk chunk) {}

                    @Override
                    public CancellationToken cancellationToken() {
                        return token;
                    }
                };
        ToolExecutionObserver toolObserver =
                new ToolExecutionObserver() {
                    @Override
                    public void onEvent(AgentMessage event) {}

                    @Override
                    public CancellationToken cancellationToken() {
                        return token;
                    }
                };

        Object[] ports = {
            new FakeDefinitionProvider(),
            (ModelGateway) (request, observer) -> null,
            modelObserver,
            new FakeAgentTool(),
            toolObserver,
            new FakeToolProvider(),
            (ToolAvailabilityProvider) () -> new ToolAvailabilitySnapshot(Set.of(), List.of()),
            (AgentEventPublisher) message -> {},
            (AgentEventPublisherFactory) execution -> message -> {},
            new FakeSessionRepository(),
            new FakeConversationRepository(),
            (SkillProvider) List::of,
            new FakeIdGenerator(),
            (TimeProvider) () -> Instant.EPOCH,
            new FakeLifecycleHook(),
            (HookResolver) (point, name) -> null,
            (ConversationWindowManager) request -> null,
            (ConversationCompactionPolicy) check -> false,
            (ConversationCompactor) request -> null
        };

        assertEquals(19, ports.length);
        assertSame(token, modelObserver.cancellationToken());
        assertSame(token, toolObserver.cancellationToken());
    }

    /** Agent列表端口不需要加载完整定义 */
    @Test
    void agentListPortDoesNotRequireLoadingFullDefinition() {
        FakeDefinitionProvider provider = new FakeDefinitionProvider();

        assertEquals(
                List.of(new AgentMetadata("agent", "Agent", "测试 Agent")), provider.listAgents());
        assertEquals(0, provider.loadCalls);
    }

    /** 生命周期和压缩端口可独立驱动正常与失败路径 */
    @Test
    void lifecycleAndCompressionPortsIndependentlyDriveSuccessAndFailurePaths() {
        ConversationCompactionPolicy falsePolicy = check -> false;
        ConversationCompactionPolicy truePolicy = check -> true;
        ConversationCompactor failingCompactor =
                request -> {
                    throw new IllegalStateException("摘要模型失败");
                };

        assertTrue(!falsePolicy.shouldCompact(null));
        assertTrue(truePolicy.shouldCompact(null));
        assertThrows(IllegalStateException.class, () -> failingCompactor.compact(null));
    }

    /** Repository命令携带稳定幂等ID */
    @Test
    void repositoryCommandsCarryStableIdempotencyIds() {
        CapturingConversationRepository repository = new CapturingConversationRepository();
        AgentMessageEntry entry =
                new AgentMessageEntry(
                        "entry-1",
                        "session",
                        1,
                        1,
                        MessageRole.USER,
                        MessageType.TEXT,
                        "你好",
                        Map.of(),
                        Instant.EPOCH);
        ConversationCompactionCommit commit =
                new ConversationCompactionCommit(
                        "session",
                        new ConversationSummary("compaction-1", "摘要", 0, 0, 1, Instant.EPOCH),
                        List.of("entry-1"),
                        List.of(entry));

        repository.append(List.of(entry));
        repository.compact(commit);

        assertEquals("entry-1", repository.entries.getFirst().entryId());
        assertEquals("compaction-1", repository.commit.summary().compactionId());
    }

    private static final class FakeDefinitionProvider implements AgentDefinitionProvider {
        private int loadCalls;

        @Override
        public AgentDefinition load(String agentKey) {
            loadCalls++;
            return null;
        }

        @Override
        public List<AgentMetadata> listAgents() {
            return List.of(new AgentMetadata("agent", "Agent", "测试 Agent"));
        }
    }

    private static final class FakeAgentTool implements AgentTool {
        @Override
        public ToolDefinition definition() {
            return null;
        }

        @Override
        public ToolResult execute(
                ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
            return null;
        }
    }

    private static final class FakeToolProvider implements ToolProvider {
        @Override
        public List<AgentTool> loadTools(AgentDefinition definition) {
            return List.of();
        }
    }

    private static final class FakeSessionRepository implements SessionRepository {
        @Override
        public Optional<SessionSnapshot> load(String sessionId) {
            return Optional.empty();
        }

        @Override
        public void save(SessionSnapshot snapshot) {}
    }

    private static final class FakeConversationRepository implements ConversationRepository {
        @Override
        public void append(List<AgentMessageEntry> entries) {}

        @Override
        public ConversationHistory load(ConversationQuery query) {
            return new ConversationHistory(query.sessionId(), Optional.empty(), List.of());
        }

        @Override
        public void compact(ConversationCompactionCommit commit) {}
    }

    private static final class CapturingConversationRepository implements ConversationRepository {
        private List<AgentMessageEntry> entries;
        private ConversationCompactionCommit commit;

        @Override
        public void append(List<AgentMessageEntry> entries) {
            this.entries = List.copyOf(entries);
        }

        @Override
        public ConversationHistory load(ConversationQuery query) {
            return new ConversationHistory(query.sessionId(), Optional.empty(), List.of());
        }

        @Override
        public void compact(ConversationCompactionCommit commit) {
            this.commit = commit;
        }
    }

    private static final class FakeIdGenerator implements IdGenerator {
        @Override
        public String newExecutionId() {
            return "execution";
        }

        @Override
        public String newEntryId() {
            return "entry";
        }

        @Override
        public String newInvocationId() {
            return "invocation";
        }

        @Override
        public String newConfirmationId() {
            return "confirmation";
        }

        @Override
        public String newSubSessionId() {
            return "sub-session";
        }

        @Override
        public String newCompactionId() {
            return "compaction";
        }
    }

    private static final class FakeLifecycleHook
            implements LifecycleHook<HookContextView, LifecycleHookResult> {
        @Override
        public String name() {
            return "fake";
        }

        @Override
        public HookTypeDescriptor descriptor() {
            return null;
        }

        @Override
        public LifecycleHookResult apply(HookContextView context) {
            return null;
        }
    }

    private static final class TestCancellationToken implements CancellationToken {
        @Override
        public boolean isCancellationRequested() {
            return false;
        }

        @Override
        public CancellationRegistration onCancel(Runnable command) {
            return () -> {};
        }
    }
}
