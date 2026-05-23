package org.gemo.apex.web.service;

import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.message.EndMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Component
public class ChatTerminalEventFactory {

    public EndMessage buildForCompletion(SuperAgentContext context) {
        return EndMessage.builder()
                .context(buildContext(context, null, null))
                .build();
    }

    public EndMessage buildForFailure(SuperAgentContext context, String errorCode, String errorMessage) {
        return EndMessage.builder()
                .context(buildContext(context, errorCode, errorMessage))
                .build();
    }

    public EndMessage buildForRequestFailure(String errorCode, String errorMessage) {
        return EndMessage.builder()
                .context(buildContext(null, errorCode, errorMessage))
                .build();
    }

    private Map<String, Object> buildContext(SuperAgentContext context, String errorCode, String errorMessage) {
        Map<String, Object> map = new HashMap<>();
        if (context != null) {
            map.put(ContextKeyEnum.MODE.getKey(),
                    context.getExecutionMode() != null ? context.getExecutionMode().getMode() : "");
            if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getCurrentStageId() != null) {
                map.put(ContextKeyEnum.STAGE_ID.getKey(), context.getCurrentStageId());
            }
        }

        if (StringUtils.hasText(errorCode)) {
            map.put(ContextKeyEnum.EXECUTION_STATUS.getKey(), ExecutionStatus.FAILED.name());
            map.put(ContextKeyEnum.ERROR_CODE.getKey(), errorCode);
            if (StringUtils.hasText(errorMessage)) {
                map.put(ContextKeyEnum.ERROR_MESSAGE.getKey(), errorMessage);
            }
            return Map.copyOf(map);
        }

        map.put(ContextKeyEnum.EXECUTION_STATUS.getKey(),
                context != null && context.getExecutionStatus() != null
                        ? context.getExecutionStatus().name()
                        : ExecutionStatus.COMPLETED.name());
        return Map.copyOf(map);
    }
}
