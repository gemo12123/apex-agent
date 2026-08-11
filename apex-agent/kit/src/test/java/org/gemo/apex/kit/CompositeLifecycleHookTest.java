package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PreToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolCallPatch;
import org.gemo.apex.common.hook.result.BlockTool;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.PreToolCallHookResult;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.hook.CompositeLifecycleHook;
import org.junit.jupiter.api.Test;

class CompositeLifecycleHookTest {
    private static final HookTypeDescriptor PRE =
            new HookTypeDescriptor(
                    HookPoint.PRE_TOOL_CALL, PreToolCallContext.class, PreToolCallHookResult.class);

    /** 组合器按显式顺序执行并在终止结果处停止 */
    @Test
    void executesCompositeInExplicitOrderAndStopsAtTerminalResult() {
        List<String> calls = new ArrayList<>();
        LifecycleHook<PreToolCallContext, PreToolCallHookResult> first =
                hook(calls, "first", continueResult());
        LifecycleHook<PreToolCallContext, PreToolCallHookResult> stop =
                hook(calls, "stop", new BlockTool("stop"));
        LifecycleHook<PreToolCallContext, PreToolCallHookResult> never =
                hook(calls, "never", continueResult());

        CompositeLifecycleHook<PreToolCallContext, PreToolCallHookResult> composite =
                new CompositeLifecycleHook<>("composite", List.of(first, stop, never));
        assertEquals("composite", composite.name());
        assertInstanceOf(BlockTool.class, composite.apply(context()));
        assertEquals(List.of("first", "stop"), calls);
    }

    /** 组合器传播异常并拒绝不同descriptor */
    @Test
    void propagatesCompositeExceptionsAndRejectsDifferentDescriptors() {
        LifecycleHook<PreToolCallContext, PreToolCallHookResult> throwing =
                new LifecycleHook<>() {
                    @Override
                    public String name() {
                        return "throwing";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return PRE;
                    }

                    @Override
                    public PreToolCallHookResult apply(PreToolCallContext context) {
                        throw new IllegalStateException("boom");
                    }
                };
        CompositeLifecycleHook<PreToolCallContext, PreToolCallHookResult> composite =
                new CompositeLifecycleHook<>("composite", List.of(throwing));
        assertThrows(IllegalStateException.class, () -> composite.apply(context()));

        LifecycleHook<PreToolCallContext, PreToolCallHookResult> mismatched =
                new LifecycleHook<>() {
                    @Override
                    public String name() {
                        return "mismatched";
                    }

                    @Override
                    public HookTypeDescriptor descriptor() {
                        return new HookTypeDescriptor(
                                HookPoint.POST_TOOL_CALL,
                                PreToolCallContext.class,
                                PreToolCallHookResult.class);
                    }

                    @Override
                    public PreToolCallHookResult apply(PreToolCallContext context) {
                        return continueResult();
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompositeLifecycleHook<>("composite", List.of(throwing, mismatched)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CompositeLifecycleHook<>(" ", List.of(throwing)));
    }

    private LifecycleHook<PreToolCallContext, PreToolCallHookResult> hook(
            List<String> calls, String name, PreToolCallHookResult result) {
        return new LifecycleHook<>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public HookTypeDescriptor descriptor() {
                return PRE;
            }

            @Override
            public PreToolCallHookResult apply(PreToolCallContext context) {
                calls.add(name);
                return result;
            }
        };
    }

    private ContinuePreToolCall continueResult() {
        return new ContinuePreToolCall(HookMutations.none(), new ToolCallPatch(Map.of()));
    }

    private PreToolCallContext context() {
        return KitFixtures.pre(
                KitFixtures.call("search", Map.of()),
                KitFixtures.binding("hook", List.of("search"), Map.of()),
                null);
    }
}
