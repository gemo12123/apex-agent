package org.gemo.apex.core.tool;

import java.util.Map;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolResult;

public final class ToolResultFactory {
    public ToolResult userDenied(ToolCall call) {
        return fixed(call, "用户拒绝执行");
    }

    public ToolResult forcedEnd(ToolCall call) {
        return fixed(call, "达到最大轮次，强制结束");
    }

    public ToolResult cancelled(ToolCall call) {
        return fixed(call, "请求已取消，工具未执行完成");
    }

    public ToolResult blocked(ToolCall call, String reason) {
        return fixed(call, "工具执行被阻断：" + reason);
    }

    public ToolResult disabled(ToolCall call) {
        return fixed(call, "工具当前未启用，无法执行");
    }

    public ToolResult unavailable(ToolCall call) {
        return fixed(call, "工具不可用");
    }

    public ToolResult executionFailed(ToolCall call, Throwable error) {
        String reason = error.getMessage();
        if (reason == null || reason.isBlank()) {
            reason = error.getClass().getSimpleName();
        }
        return fixed(call, "工具执行失败：" + reason);
    }

    private ToolResult fixed(ToolCall call, String content) {
        return new ToolResult(call.toolCallId(), call.name(), content, Map.of());
    }
}
