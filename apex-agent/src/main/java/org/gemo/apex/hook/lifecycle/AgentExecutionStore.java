package org.gemo.apex.hook.lifecycle;

import java.util.List;
import java.util.Optional;

public interface AgentExecutionStore {
    long nextTurnNo();

    void saveTurn(AgentTurn turn);

    void saveIteration(AgentIteration iteration);

    Optional<AgentTurn> findTurn(long turnNo);

    Optional<AgentIteration> findIteration(long turnNo, int iterationNo);

    List<AgentIteration> findIterations(long turnNo);
}
