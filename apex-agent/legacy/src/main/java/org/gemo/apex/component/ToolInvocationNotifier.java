package org.gemo.apex.component;

import org.gemo.apex.context.SuperAgentContext;
import org.springframework.ai.tool.ToolCallback;

public interface ToolInvocationNotifier {

    void beforeExecution(ToolCallback toolCallback, SuperAgentContext context, String invocationId);

    void afterExecution(ToolCallback toolCallback, SuperAgentContext context, String invocationId);
}
