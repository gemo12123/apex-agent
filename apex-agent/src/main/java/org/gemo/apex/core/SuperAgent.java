package org.gemo.apex.core;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ExecutionStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.core.engine.ExecutionFinalizer;
import org.gemo.apex.core.engine.HumanInLoopResumer;
import org.gemo.apex.core.engine.SuperAgentLoopRunner;
import org.gemo.apex.exception.HumanInTheLoopException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SuperAgent {

    private final HumanInLoopResumer humanInLoopResumer;
    private final SuperAgentLoopRunner superAgentLoopRunner;
    private final ExecutionFinalizer executionFinalizer;

    public SuperAgent(HumanInLoopResumer humanInLoopResumer,
            SuperAgentLoopRunner superAgentLoopRunner,
            ExecutionFinalizer executionFinalizer) {
        this.humanInLoopResumer = humanInLoopResumer;
        this.superAgentLoopRunner = superAgentLoopRunner;
        this.executionFinalizer = executionFinalizer;
    }

    public void execute(SuperAgentContext context) {
        humanInLoopResumer.resume(context);
        boolean finalized = false;

        try {
            superAgentLoopRunner.run(context);
        } catch (HumanInTheLoopException ex) {
            executionFinalizer.finalizeTurn(context);
            finalized = true;
            throw ex;
        } catch (RuntimeException ex) {
            if (context.getExecutionStatus() == ExecutionStatus.IN_PROGRESS) {
                context.setExecutionStatus(ExecutionStatus.FAILED);
            }
            log.error("SuperAgent execution failed, sessionId={}", context.getSessionId(), ex);
            throw ex;
        } finally {
            if (!finalized) {
                executionFinalizer.finalizeTurn(context);
            }
        }
    }
}
