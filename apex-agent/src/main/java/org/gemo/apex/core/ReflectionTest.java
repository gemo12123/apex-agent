package org.gemo.apex.core;

import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.chat.messages.SystemMessage;

import java.lang.reflect.Method;

public class ReflectionTest {
    public static void main(String[] args) {
        System.out.println("--- ToolDefinition Methods ---");
        for (Method m : ToolDefinition.class.getMethods()) {
            System.out.println(m.getName());
        }

        System.out.println("--- SystemMessage Methods ---");
        for (Method m : SystemMessage.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
