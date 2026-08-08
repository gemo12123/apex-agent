package org.gemo.apex.core.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.gemo.apex.common.tool.ToolCall;
import org.junit.jupiter.api.Test;

class ToolResultFactoryTest {
    /** 三类固定结果保留原关联并使用空metadata */
    @Test
    void preservesOriginalAssociationAndEmptyMetadataForThreeFixedResults() {
        ToolCall call = new ToolCall("call-1", "tool", 0, Map.of(), Map.of());
        ToolResultFactory factory = new ToolResultFactory();

        var denied = factory.userDenied(call);
        var forced = factory.forcedEnd(call);
        var cancelled = factory.cancelled(call);

        assertAll(
                () -> assertEquals("用户拒绝执行", denied.content()),
                () -> assertEquals("达到最大轮次，强制结束", forced.content()),
                () -> assertEquals("请求已取消，工具未执行完成", cancelled.content()),
                () -> assertEquals("call-1", denied.toolCallId()),
                () -> assertEquals("tool", denied.toolName()),
                () -> assertTrue(denied.metadata().isEmpty()),
                () -> assertTrue(forced.metadata().isEmpty()),
                () -> assertTrue(cancelled.metadata().isEmpty()));
    }
}
