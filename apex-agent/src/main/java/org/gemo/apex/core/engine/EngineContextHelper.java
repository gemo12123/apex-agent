package org.gemo.apex.core.engine;

import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.HashMap;
import java.util.Map;

final class EngineContextHelper {

    private EngineContextHelper() {
    }

    static Map<String, Object> buildMessageContext(SuperAgentContext context, String... extraEntries) {
        if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
            return Map.of();
        }
        Map<String, Object> map = new HashMap<>();
        map.put(ContextKeyEnum.MODE.getKey(),
                context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
        if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
            map.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
        }
        for (int i = 0; i + 1 < extraEntries.length; i += 2) {
            map.put(extraEntries[i], extraEntries[i + 1]);
        }
        return Map.copyOf(map);
    }

    static boolean hasToolCalls(AssistantMessage message) {
        return message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty();
    }
}
