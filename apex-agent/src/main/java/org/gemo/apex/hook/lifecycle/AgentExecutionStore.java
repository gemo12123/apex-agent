package org.gemo.apex.hook.lifecycle;

import java.util.List;
import java.util.Optional;

public interface AgentExecutionStore {
    long nextTurnNo();

    void saveTurn(AgentTurn turn);

    void saveTrace(AgentTrace trace);

    Optional<AgentTurn> findTurn(long turnNo);

    Optional<AgentTrace> findTrace(long turnNo, int traceNo);

    List<AgentTrace> findTraces(long turnNo);
}
