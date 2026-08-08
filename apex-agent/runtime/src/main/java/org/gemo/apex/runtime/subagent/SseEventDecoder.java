package org.gemo.apex.runtime.subagent;

import java.util.*;

public final class SseEventDecoder {
    private final StringBuilder data = new StringBuilder();

    public List<String> accept(String l) {
        if (l == null || l.isEmpty()) {
            if (data.isEmpty()) return List.of();
            var s = data.toString();
            data.setLength(0);
            return List.of(s);
        }
        if (l.startsWith(":")) return List.of();
        if (l.startsWith("data:")) {
            if (!data.isEmpty()) data.append('\n');
            data.append(l.substring(5).stripLeading());
        }
        return List.of();
    }

    public List<String> finish() {
        return accept("");
    }
}
