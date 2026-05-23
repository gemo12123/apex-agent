package org.gemo.apex.core;

import org.gemo.apex.context.SuperAgentContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SuperAgentExecutor {

    @Autowired
    private SuperAgent superAgent;

    public void execute(SuperAgentContext context) {
        superAgent.execute(context);
    }
}
