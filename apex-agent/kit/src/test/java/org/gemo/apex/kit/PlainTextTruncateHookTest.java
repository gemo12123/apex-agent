package org.gemo.apex.kit;

import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.message.MessageType;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.hook.PlainTextTruncateHook;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlainTextTruncateHookTest {
    /**
     * 边界内文本保持不变且超长文本包含计入上限的截断标识
     */
    @Test
    void preservesTextWithinLimitAndMarksTruncationWithinLimit() {
        PlainTextTruncateHook hook = new PlainTextTruncateHook(5);
        assertEquals("12345", apply(hook, new ToolResult("call-1", "tool", "12345", Map.of())).patch().content());
        String truncated = apply(hook, new ToolResult("call-1", "tool", "123456", Map.of())).patch().content();
        assertEquals("1234…", truncated);
        assertEquals(5, truncated.codePointCount(0, truncated.length()));
    }

    /**
     * 按Unicode码点截断且不切断emoji
     */
    @Test
    void truncatesByUnicodeCodePointsWithoutSplittingEmoji() {
        String truncated = apply(new PlainTextTruncateHook(3),
                new ToolResult("call-1", "tool", "A😀BC", Map.of())).patch().content();
        assertEquals("A😀…", truncated);
        assertEquals(3, truncated.codePointCount(0, truncated.length()));
    }

    /**
     * 非文本结果即使超长也保持不变
     */
    @Test
    void keepsNonTextResultsUnchangedWhenOverLimit() {
        ToolResult result = new ToolResult("call-1", "tool", "123456",
                Map.of(PlainTextTruncateHook.MESSAGE_TYPE_METADATA_KEY, MessageType.TOOL_RESULT));
        ContinuePostToolCall patched = apply(new PlainTextTruncateHook(3), result);
        assertEquals(result.content(), patched.patch().content());
        assertEquals(result.metadata(), patched.patch().metadata());
    }

    /**
     * 非正长度在构造时失败
     */
    @Test
    void rejectsNonPositiveLengthAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new PlainTextTruncateHook(0));
        assertThrows(IllegalArgumentException.class, () -> new PlainTextTruncateHook(-1));
    }

    private ContinuePostToolCall apply(PlainTextTruncateHook hook, ToolResult result) {
        return assertInstanceOf(ContinuePostToolCall.class, hook.apply(KitFixtures.post(result)));
    }
}
