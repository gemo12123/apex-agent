package org.gemo.apex.kit.subagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;
import org.gemo.apex.protocol.event.InvocationChangeMessage;
import org.gemo.apex.protocol.event.InvocationDeclaredMessage;
import org.junit.jupiter.api.Test;

class HttpSubAgentToolTest {
    @Test
    void rewritesOuterOwnerAndAddsParentOnlyToDirectChildren() {
        InvocationDeclaredMessage source =
                InvocationDeclaredMessage.builder()
                        .context(
                                Map.of(
                                        "mode", "react",
                                        "invocation_id", "child-owner",
                                        "executor", "child-tool"))
                        .messages(List.of(detail("child", null), detail("grandchild", "child")))
                        .build();

        InvocationDeclaredMessage forwarded =
                assertInstanceOf(
                        InvocationDeclaredMessage.class,
                        HttpSubAgentTool.forwardedInvocation(source, "parent"));

        assertEquals("parent", forwarded.getContext().get("invocation_id"));
        assertEquals("child-tool", forwarded.getContext().get("executor"));
        assertEquals("child", forwarded.getMessages().getFirst().getInvocationId());
        assertEquals("parent", forwarded.getMessages().getFirst().getParentInvocationId());
        assertEquals("child", forwarded.getMessages().getLast().getParentInvocationId());
    }

    @Test
    void rewritesChangeOwnerWithoutChangingActualTargetNode() {
        InvocationChangeMessage source =
                InvocationChangeMessage.builder()
                        .context(Map.of("mode", "react", "invocation_id", "child-owner"))
                        .messages(
                                List.of(
                                        InvocationChangeMessage.InvocationChangeDetail.builder()
                                                .changeType("STATUS_CHANGE")
                                                .invocationId("grandchild")
                                                .status("COMPLETE")
                                                .build()))
                        .build();

        InvocationChangeMessage forwarded =
                assertInstanceOf(
                        InvocationChangeMessage.class,
                        HttpSubAgentTool.forwardedInvocation(source, "parent"));

        assertEquals("parent", forwarded.getContext().get("invocation_id"));
        assertEquals("grandchild", forwarded.getMessages().getFirst().getInvocationId());
    }

    private InvocationDeclaredMessage.InvocationMessage detail(
            String invocationId, String parentInvocationId) {
        return InvocationDeclaredMessage.InvocationMessage.builder()
                .invocationId(invocationId)
                .parentInvocationId(parentInvocationId)
                .name(invocationId)
                .invocationType("tool")
                .clickEffect("none")
                .content("{}")
                .complete(false)
                .renderType("json")
                .build();
    }
}
