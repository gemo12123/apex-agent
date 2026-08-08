package org.gemo.apex.kit.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.intervention.QuestionSubmission;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;

public final class AskHumanTool implements AgentTool {
    public static final String NAME = "ask_human";
    private static final String INPUT_SCHEMA =
            """
            {"type":"object","required":["questions"],"properties":{"questions":{"type":"array","minItems":1,"items":{"type":"object","required":["index","input_type","question"],"properties":{"index":{"type":"integer","minimum":0},"input_type":{"type":"string"},"question":{"type":"string"},"description":{"type":"string"},"options":{"type":"array","items":{"type":"object"}}}}}}}
            """
                    .strip();
    private static final ToolDefinition DEFINITION =
            new ToolDefinition(NAME, "当信息不全或必须由用户确认时，挂起任务并向用户提问。", INPUT_SCHEMA, Map.of());

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(
            ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
        if (!NAME.equals(call.name())) {
            throw new IllegalArgumentException("AskHumanTool 只能执行 ask_human 调用");
        }
        if (!(context.humanSubmission() instanceof QuestionSubmission submission)) {
            throw new IllegalStateException("ask_human 恢复执行缺少 QuestionSubmission");
        }
        if (!call.toolCallId().equals(submission.toolCallId())) {
            throw new IllegalStateException("QuestionSubmission.toolCallId 与当前调用不一致");
        }

        LinkedHashMap<String, Object> answers = new LinkedHashMap<>();
        submission.answers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(answerKeyComparator()))
                .forEach(entry -> answers.put(entry.getKey(), normalizeAnswer(entry.getValue())));
        return new ToolResult(
                call.toolCallId(),
                call.name(),
                JsonUtils.toJson(Map.of("answers", answers)),
                Map.of());
    }

    private Comparator<String> answerKeyComparator() {
        return Comparator.comparingInt(this::numericIndex).thenComparing(Comparator.naturalOrder());
    }

    private int numericIndex(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private Object normalizeAnswer(Object answer) {
        if (answer instanceof String) {
            return answer;
        }
        if (answer instanceof List<?> values) {
            List<String> normalized = new ArrayList<>(values.size());
            for (Object value : values) {
                if (!(value instanceof String stringValue)) {
                    throw new IllegalStateException("ask_human 多选答案只能包含字符串");
                }
                normalized.add(stringValue);
            }
            return List.copyOf(normalized);
        }
        throw new IllegalStateException("ask_human 答案只能是字符串或字符串数组");
    }
}
