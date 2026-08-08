package org.gemo.apex.kit;

import org.gemo.apex.common.hook.result.BlockTool;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.RequestHumanIntervention;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSubmission;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.hook.AskHumanInterventionHook;
import org.gemo.apex.kit.tool.AskHumanTool;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AskHumanContractTest {
    private final AskHumanInterventionHook hook = new AskHumanInterventionHook();
    private final AskHumanTool tool = new AskHumanTool();

    /**
     * 首次调用产生排序稳定的提问介入请求
     */
    @Test
    void createsSortedStableQuestionInterventionOnFirstInvocation() {
        ToolCall call = askCall();

        RequestHumanIntervention result = assertInstanceOf(RequestHumanIntervention.class,
                hook.apply(KitFixtures.pre(call,
                        KitFixtures.binding(AskHumanInterventionHook.REGISTRATION_NAME,
                                List.of(AskHumanTool.NAME), Map.of()), null)));

        QuestionInterventionRequest request = assertInstanceOf(QuestionInterventionRequest.class, result.request());
        assertEquals("call-1", request.toolCallId());
        assertEquals(List.of("选择环境", "输入名称"),
                request.questions().stream().map(question -> question.question()).toList());
        assertEquals(List.of(Map.of("label", "测试", "value", "test")),
                request.questions().getFirst().options());
    }

    /**
     * 恢复误重入时继续且真实工具只返回用户答案
     */
    @Test
    void continuesOnResumeReentrancyAndReturnsOnlyUserAnswersFromRealTool() {
        ToolCall call = askCall();
        LinkedHashMap<String, Object> answers = new LinkedHashMap<>();
        answers.put("10", List.of("a", "b"));
        answers.put("2", "test");
        QuestionSubmission submission = new QuestionSubmission(call.toolCallId(), answers);

        assertInstanceOf(ContinuePreToolCall.class,
                hook.apply(KitFixtures.pre(call,
                        KitFixtures.binding(AskHumanInterventionHook.REGISTRATION_NAME,
                                List.of(AskHumanTool.NAME), Map.of()), submission)));
        ToolResult result = tool.execute(call, KitFixtures.execution(submission), KitFixtures.OBSERVER);

        assertEquals("call-1", result.toolCallId());
        assertEquals(AskHumanTool.NAME, result.toolName());
        assertEquals("{\"answers\":{\"2\":\"test\",\"10\":[\"a\",\"b\"]}}", result.content());
        assertTrue(result.metadata().isEmpty());
    }

    /**
     * 可选问题没有答案时返回空答案对象
     */
    @Test
    void returnsEmptyAnswerObjectWhenOptionalQuestionHasNoAnswer() {
        ToolCall call = askCall();
        ToolResult result = tool.execute(call,
                KitFixtures.execution(new QuestionSubmission(call.toolCallId(), Map.of())),
                KitFixtures.OBSERVER);
        assertEquals("{\"answers\":{}}", result.content());
    }

    /**
     * 非法问题参数阻断工具且不创建空介入
     */
    @Test
    void blocksToolAndAvoidsEmptyInterventionForInvalidQuestionParameters() {
        ToolCall call = KitFixtures.call(AskHumanTool.NAME, Map.of("questions", List.of()));
        BlockTool result = assertInstanceOf(BlockTool.class,
                hook.apply(KitFixtures.pre(call,
                        KitFixtures.binding(AskHumanInterventionHook.REGISTRATION_NAME,
                                List.of(AskHumanTool.NAME), Map.of()), null)));
        assertTrue(result.reason().contains("questions"));
    }

    /**
     * 缺少或错配回复时真实工具失败
     */
    @Test
    void realToolFailsWhenResponseIsMissingOrMismatched() {
        ToolCall call = askCall();
        assertThrows(IllegalStateException.class,
                () -> tool.execute(call, KitFixtures.execution(null), KitFixtures.OBSERVER));
        QuestionSubmission mismatch = new QuestionSubmission("other-call", Map.of("0", "answer"));
        assertThrows(IllegalStateException.class,
                () -> tool.execute(call, KitFixtures.execution(mismatch), KitFixtures.OBSERVER));
    }

    /**
     * 非askHuman调用不触发提问Hook
     */
    @Test
    void doesNotTriggerAskHumanHookForNonAskHumanCalls() {
        ToolCall call = KitFixtures.call("search", Map.of());
        assertInstanceOf(ContinuePreToolCall.class,
                hook.apply(KitFixtures.pre(call,
                        KitFixtures.binding(AskHumanInterventionHook.REGISTRATION_NAME,
                                List.of("*"), Map.of()), null)));
    }

    private ToolCall askCall() {
        return KitFixtures.call(AskHumanTool.NAME, Map.of("questions", List.of(
                Map.of("index", 2, "input_type", "TEXT_INPUT", "question", "输入名称"),
                Map.of("index", 1, "input_type", "SINGLE_SELECT", "question", "选择环境",
                        "description", "运行环境", "options",
                        List.of(Map.of("label", "测试", "value", "test"))))));
    }
}
