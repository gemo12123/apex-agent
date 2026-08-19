package org.gemo.apex.platform.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.intervention.QuestionInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.snapshot.PreparedToolCallDisposition;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.gemo.apex.protocol.event.HumanInterventionMessage;
import org.gemo.apex.protocol.event.detail.AskHumanInterventionDetail;
import org.gemo.apex.protocol.event.detail.AskHumanOption;
import org.gemo.apex.protocol.event.detail.AskHumanQuestionDetail;
import org.gemo.apex.protocol.event.detail.HumanInterventionDetail;
import org.gemo.apex.protocol.request.SessionStateView;

public final class SessionStateViewMapper {
    public SessionStateView map(SessionSnapshot snapshot) {
        HumanInterventionMessage pending =
                snapshot.suspendedToolBatch() == null ? null : interaction(snapshot);
        return new SessionStateView(
                snapshot.sessionId(), snapshot.agentKey(), snapshot.status().name(), pending);
    }

    private HumanInterventionMessage interaction(SessionSnapshot snapshot) {
        List<HumanInterventionDetail> messages = new ArrayList<>();
        for (var prepared : snapshot.suspendedToolBatch().toolCalls()) {
            if (prepared.disposition() != PreparedToolCallDisposition.INTERVENTION) {
                continue;
            }
            if (prepared.intervention() instanceof QuestionInterventionRequest request) {
                List<AskHumanQuestionDetail> questions =
                        request.questions().stream()
                                .map(
                                        spec -> {
                                            List<AskHumanOption> options =
                                                    spec.options().stream()
                                                            .<AskHumanOption>map(
                                                                    option ->
                                                                            AskHumanOption.builder()
                                                                                    .label(
                                                                                            String
                                                                                                    .valueOf(
                                                                                                            option
                                                                                                                    .get(
                                                                                                                            "label")))
                                                                                    .description(
                                                                                            option
                                                                                                                    .get(
                                                                                                                            "description")
                                                                                                            == null
                                                                                                    ? null
                                                                                                    : String
                                                                                                            .valueOf(
                                                                                                                    option
                                                                                                                            .get(
                                                                                                                                    "description")))
                                                                                    .build())
                                                            .toList();
                                            return AskHumanQuestionDetail.builder()
                                                    .inputType(spec.inputType())
                                                    .question(spec.question())
                                                    .description(spec.description())
                                                    .options(options)
                                                    .build();
                                        })
                                .toList();
                messages.add(
                        AskHumanInterventionDetail.builder()
                                .toolCallId(request.toolCallId())
                                .invocationId(prepared.invocationId())
                                .toolName(prepared.toolName())
                                .questions(questions)
                                .build());
            } else if (prepared.intervention()
                    instanceof ToolConfirmationInterventionRequest confirmation) {
                messages.add(confirmation.presentation());
            } else {
                throw new IllegalStateException("不支持的挂起交互类型");
            }
        }
        if (messages.isEmpty()) {
            throw new IllegalStateException("人工介入批次不包含待处理项");
        }
        return HumanInterventionMessage.builder()
                .context(Map.of("mode", "react"))
                .messages(messages)
                .build();
    }
}
