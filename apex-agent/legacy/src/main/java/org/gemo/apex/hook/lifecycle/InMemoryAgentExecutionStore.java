package org.gemo.apex.hook.lifecycle;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class InMemoryAgentExecutionStore implements AgentExecutionStore {

    private static final AtomicLong TURN_SEQUENCE = new AtomicLong();
    private final Map<Long, AgentTurn> turns = new ConcurrentHashMap<>();
    private final Map<String, AgentIteration> iterations = new ConcurrentHashMap<>();

    @Override
    public long nextTurnNo() {
        return TURN_SEQUENCE.incrementAndGet();
    }

    @Override
    public void saveTurn(AgentTurn turn) {
        if (turn != null) {
            turns.put(turn.getTurnNo(), turn);
        }
    }

    @Override
    public void saveIteration(AgentIteration iteration) {
        if (iteration != null) {
            iterations.put(key(iteration.getTurnNo(), iteration.getIterationNo()), iteration);
        }
    }

    @Override
    public Optional<AgentTurn> findTurn(long turnNo) {
        return Optional.ofNullable(turns.get(turnNo));
    }

    @Override
    public Optional<AgentIteration> findIteration(long turnNo, int iterationNo) {
        return Optional.ofNullable(iterations.get(key(turnNo, iterationNo)));
    }

    @Override
    public List<AgentIteration> findIterations(long turnNo) {
        return iterations.values().stream()
                .filter(iteration -> iteration.getTurnNo() == turnNo)
                .sorted(Comparator.comparingInt(AgentIteration::getIterationNo))
                .toList();
    }

    private String key(long turnNo, int iterationNo) {
        return turnNo + ":" + iterationNo;
    }
}
