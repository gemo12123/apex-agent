package org.gemo.apex.extension.tool;

import org.gemo.apex.common.tool.ToolAvailabilitySnapshot;

public interface ToolAvailabilityProvider {
    /**
     * 返回不可变健康快照；端口只报告事实，不修改 Agent 定义或 Session。
     */
    ToolAvailabilitySnapshot current();
}
