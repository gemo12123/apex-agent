package org.gemo.apex.kit;

import org.gemo.apex.common.hook.HookBinding;
import org.gemo.apex.common.hook.result.ContinuePreToolCall;
import org.gemo.apex.common.hook.result.RequestHumanIntervention;
import org.gemo.apex.common.intervention.ConfirmationDecision;
import org.gemo.apex.common.intervention.ToolConfirmationInterventionRequest;
import org.gemo.apex.common.intervention.ToolConfirmationSubmission;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.kit.hook.ToolConfirmHook;
import org.gemo.apex.protocol.event.detail.EditableFieldInputType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ToolConfirmationContractTest {
    private final ToolConfirmHook hook = new ToolConfirmHook();

    @Test
    void 构造完整确认展示契约并使用core预分配标识() {
        ToolCall call = KitFixtures.call("meeting_tool", Map.of("room", "A1001", "date", "2026-08-02"));
        HookBinding binding = KitFixtures.binding(ToolConfirmHook.REGISTRATION_NAME, List.of("meeting_tool"), Map.of(
                "title", "确认会议", "description", "创建会议", "risk-level", "medium",
                "tool-display-name", "会议工具", "confirm-label", "批准", "deny-label", "拒绝",
                "display-fields", List.of(Map.of("key", "date", "label", "日期")),
                "editable-fields", List.of(Map.of("key", "room", "label", "会议室",
                        "input-type", "single-select", "required", true,
                        "options", List.of(Map.of("label", "A1001"))))));

        RequestHumanIntervention result = assertInstanceOf(RequestHumanIntervention.class,
                hook.apply(KitFixtures.pre(call, binding, null)));
        ToolConfirmationInterventionRequest request = assertInstanceOf(
                ToolConfirmationInterventionRequest.class, result.request());

        assertEquals("intervention-1", request.confirmationId());
        assertEquals("invocation-1", request.invocationId());
        assertEquals(Set.of("room"), request.editableArgumentKeys());
        assertEquals("会议工具", request.presentation().getToolDisplayName());
        assertEquals("medium", request.presentation().getRiskLevel());
        assertTrue(request.presentation().isEditable());
        assertEquals(EditableFieldInputType.SINGLE_SELECT,
                request.presentation().getEditableFields().getFirst().getInputType());
        assertEquals("2026-08-02", request.presentation().getDisplayFields().getFirst().getValue());
    }

    @Test
    void 默认展示值确定且不可编辑() {
        ToolCall call = KitFixtures.call("search", Map.of("query", "apex"));
        ToolConfirmationInterventionRequest request = assertInstanceOf(ToolConfirmationInterventionRequest.class,
                assertInstanceOf(RequestHumanIntervention.class,
                        hook.apply(KitFixtures.pre(call,
                                KitFixtures.binding(ToolConfirmHook.REGISTRATION_NAME,
                                        List.of("search"), Map.of()), null))).request());
        assertEquals("工具调用确认", request.presentation().getTitle());
        assertEquals("MEDIUM", request.presentation().getRiskLevel());
        assertEquals("确认执行", request.presentation().getConfirmLabel());
        assertEquals("取消", request.presentation().getDenyLabel());
        assertFalse(request.presentation().isEditable());
    }

    @Test
    void 恢复误重入只继续不生成状态机结果() {
        ToolCall call = KitFixtures.call("search", Map.of("query", "apex"));
        ToolConfirmationSubmission submission = new ToolConfirmationSubmission(call.toolCallId(),
                "intervention-1", ConfirmationDecision.CONFIRM, Map.of("query", "updated"));
        assertInstanceOf(ContinuePreToolCall.class,
                hook.apply(KitFixtures.pre(call,
                        KitFixtures.binding(ToolConfirmHook.REGISTRATION_NAME,
                                List.of("search"), Map.of()), submission)));
    }

    @Test
    void 重复或不存在的可编辑字段在构造确认前失败() {
        ToolCall call = KitFixtures.call("search", Map.of("query", "apex"));
        assertThrows(IllegalArgumentException.class, () -> hook.apply(KitFixtures.pre(call,
                KitFixtures.binding(ToolConfirmHook.REGISTRATION_NAME, List.of("search"), Map.of(
                        "editable-fields", List.of(
                                Map.of("key", "query", "label", "查询"),
                                Map.of("key", "query", "label", "重复")))), null)));
        assertThrows(IllegalArgumentException.class, () -> hook.apply(KitFixtures.pre(call,
                KitFixtures.binding(ToolConfirmHook.REGISTRATION_NAME, List.of("search"), Map.of(
                        "display-fields", List.of(Map.of("key", "missing", "label", "缺失")))), null)));
    }
}
