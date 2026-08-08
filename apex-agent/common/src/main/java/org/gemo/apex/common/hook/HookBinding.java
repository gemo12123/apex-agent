package org.gemo.apex.common.hook;

import static org.gemo.apex.common.support.DomainValues.*;

import java.util.List;
import java.util.Map;
import org.gemo.apex.common.support.DomainValues;

public record HookBinding(
        String id,
        String name,
        int order,
        boolean enabled,
        List<String> tools,
        Map<String, Object> options) {
    public HookBinding {
        id = required(id, "id");
        name = required(name, "name");
        nonNegative(order, "order");
        tools =
                immutableList(tools, "tools").stream()
                        .map(tool -> required(tool, "tools"))
                        .toList();
        options = DomainValues.jsonMap(options, "options");
    }
}
