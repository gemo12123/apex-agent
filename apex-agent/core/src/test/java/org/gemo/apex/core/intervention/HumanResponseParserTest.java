package org.gemo.apex.core.intervention;

import org.gemo.apex.common.intervention.ConfirmationDecision;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.QuestionSpec;
import org.gemo.apex.common.intervention.QuestionSubmission;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationSubmission;
import org.gemo.apex.core.exception.InvalidHumanResponseException;
import org.gemo.apex.protocol.event.detail.ToolConfirmationDetail;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HumanResponseParserTest {
    private final HumanResponseParser parser = new HumanResponseParser();

    @Test
    void resolvesAllQuestionDefaultsOnlyWhenTheInterventionIsProcessed() {
        QuestionInterventionRequest request = new QuestionInterventionRequest("call-1", List.of(
                question("TEXT_INPUT", List.of()),
                question("SINGLE_SELECT", List.of(option("首选"), option("次选"))),
                question("MULTI_SELECT", List.of(option("甲"), option("乙"))),
                question("CONFIRM", List.of()),
                question("SINGLE_SELECT", List.of()),
                question("MULTI_SELECT", List.of())));

        QuestionSubmission submission = (QuestionSubmission) parser.parse(null, request);

        assertEquals(Map.of(
                "0", "用户未提供输入",
                "1", "首选",
                "2", List.of("甲"),
                "3", "确认",
                "4", "用户未提供选择",
                "5", List.of("用户未提供选择")), submission.answers());
    }

    @Test
    void treatsMissingBlankAndEmptyQuestionAnswersAsDefaults() {
        QuestionInterventionRequest request = new QuestionInterventionRequest("call-1", List.of(
                question("TEXT_INPUT", List.of()),
                question("SINGLE_SELECT", List.of(option("默认选项"))),
                question("MULTI_SELECT", List.of(option("默认多选")))));

        QuestionSubmission submission = (QuestionSubmission) parser.parse(Map.of(
                "interaction_type", "ASK_HUMAN",
                "answers", Map.of("0", "  ", "2", List.of())), request);

        assertEquals(Map.of(
                "0", "用户未提供输入",
                "1", "默认选项",
                "2", List.of("默认多选")), submission.answers());
    }

    @Test
    void defaultsMissingToolConfirmationToApprovalWithoutArgumentChanges() {
        ToolConfirmationSubmission submission = (ToolConfirmationSubmission) parser.parse(
                null, confirmation());

        assertEquals(ConfirmationDecision.CONFIRM, submission.decision());
        assertEquals(Map.of(), submission.updatedArguments());
    }

    @Test
    void rejectsExplicitResponsesWithTheWrongTypeOrConfirmationId() {
        assertThrows(InvalidHumanResponseException.class, () -> parser.parse(Map.of(
                "interaction_type", "TOOL_CONFIRMATION",
                "confirmation_id", "wrong",
                "decision", "APPROVE"), confirmation()));
        assertThrows(InvalidHumanResponseException.class, () -> parser.parse(Map.of(
                "interaction_type", "TOOL_CONFIRMATION",
                "answers", Map.of()), new QuestionInterventionRequest("call-1", List.of(
                question("TEXT_INPUT", List.of())))));
    }

    private QuestionSpec question(String inputType, List<Map<String, Object>> options) {
        return new QuestionSpec(inputType, "问题", null, options);
    }

    private Map<String, Object> option(String label) {
        return Map.of("label", label);
    }

    private ToolConfirmationInterventionRequest confirmation() {
        ToolConfirmationDetail presentation = ToolConfirmationDetail.builder()
                .confirmationId("confirmation-1")
                .toolCallId("call-1")
                .invocationId("invocation-1")
                .toolName("meeting_tool")
                .toolDisplayName("会议工具")
                .title("确认执行")
                .riskLevel("MEDIUM")
                .editable(false)
                .confirmLabel("确认")
                .denyLabel("拒绝")
                .displayFields(List.of())
                .editableFields(List.of())
                .build();
        return new ToolConfirmationInterventionRequest("call-1", "confirmation-1",
                "invocation-1", "meeting_tool", presentation, Set.of());
    }
}
