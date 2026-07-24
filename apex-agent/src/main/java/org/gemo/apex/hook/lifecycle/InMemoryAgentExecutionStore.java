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
    private final Map<String, AgentTrace> traces = new ConcurrentHashMap<>();

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
    public void saveTrace(AgentTrace trace) {
        if (trace != null) {
            traces.put(key(trace.getTurnNo(), trace.getTraceNo()), trace);
        }
    }

    @Override
    public Optional<AgentTurn> findTurn(long turnNo) {
        return Optional.ofNullable(turns.get(turnNo));
    }

    @Override
    public Optional<AgentTrace> findTrace(long turnNo, int traceNo) {
        return Optional.ofNullable(traces.get(key(turnNo, traceNo)));
    }

    @Override
    public List<AgentTrace> findTraces(long turnNo) {
        return traces.values().stream()
                .filter(trace -> trace.getTurnNo() == turnNo)
                .sorted(Comparator.comparingInt(AgentTrace::getTraceNo))
                .toList();
    }

    private String key(long turnNo, int traceNo) {
        return turnNo + ":" + traceNo;
    }
}
