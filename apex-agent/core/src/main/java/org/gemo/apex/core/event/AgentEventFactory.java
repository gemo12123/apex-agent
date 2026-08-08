package org.gemo.apex.core.event;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.snapshot.PreparedToolCallDisposition;
import org.gemo.apex.common.snapshot.SuspendedToolBatch;
import org.gemo.apex.protocol.event.*;
import org.gemo.apex.protocol.event.detail.AskHumanInterventionDetail;
import org.gemo.apex.protocol.event.detail.AskHumanOption;
import org.gemo.apex.protocol.event.detail.AskHumanQuestionDetail;
import org.gemo.apex.protocol.event.detail.HumanInterventionDetail;

public final class AgentEventFactory {
    public StreamContentMessage streamContent(String contentId, String delta) {
        return StreamContentMessage.builder()
                .context(context(Map.of("content_id", required(contentId, "contentId"))))
                .messages(List.of(new StreamContentMessage.ContentMessage(delta)))
                .build();
    }

    public HumanInterventionMessage humanIntervention(SuspendedToolBatch batch) {
        List<HumanInterventionDetail> details = new ArrayList<>();
        for (var prepared : batch.toolCalls()) {
            if (prepared.disposition() != PreparedToolCallDisposition.INTERVENTION) {
                continue;
            }
            if (prepared.intervention() instanceof QuestionInterventionRequest request) {
                List<AskHumanQuestionDetail> questions = new ArrayList<>();
                for (var question : request.questions()) {
                    List<AskHumanOption> options = new ArrayList<>();
                    for (Map<String, Object> option : question.options()) {
                        options.add(
                                AskHumanOption.builder()
                                        .label(String.valueOf(option.get("label")))
                                        .description(
                                                option.get("description") == null
                                                        ? null
                                                        : String.valueOf(option.get("description")))
                                        .build());
                    }
                    questions.add(
                            AskHumanQuestionDetail.builder()
                                    .inputType(question.inputType())
                                    .question(question.question())
                                    .description(question.description())
                                    .options(options)
                                    .build());
                }
                details.add(
                        AskHumanInterventionDetail.builder()
                                .toolCallId(request.toolCallId())
                                .invocationId(prepared.invocationId())
                                .toolName(prepared.toolName())
                                .questions(questions)
                                .build());
            } else if (prepared.intervention()
                    instanceof ToolConfirmationInterventionRequest confirmation) {
                details.add(confirmation.presentation());
            } else {
                throw new IllegalArgumentException("不支持的人工介入类型");
            }
        }
        if (details.isEmpty()) {
            throw new IllegalArgumentException("人工介入批次不能为空");
        }
        return HumanInterventionMessage.builder()
                .context(context(Map.of()))
                .messages(details)
                .build();
    }

    public EndMessage end() {
        return EndMessage.builder().build();
    }

    private Map<String, Object> context(Map<String, Object> extra) {
        Map<String, Object> result = new LinkedHashMap<>(extra);
        result.put("mode", "react");
        return Map.copyOf(result);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
