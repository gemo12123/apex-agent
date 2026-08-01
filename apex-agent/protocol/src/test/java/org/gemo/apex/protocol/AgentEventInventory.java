package org.gemo.apex.protocol;

import org.gemo.apex.protocol.event.AgentEventType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

final class AgentEventInventory {
    private AgentEventInventory() {
    }

    static Map<String, String> eventTypes() throws IllegalAccessException {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : AgentEventType.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                values.put(field.getName(), (String) field.get(null));
            }
        }
        return values;
    }
}
