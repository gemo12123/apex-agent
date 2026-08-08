package org.gemo.apex.platform.execution;

import org.gemo.apex.platform.security.UserContextHolder;
import org.springframework.core.task.TaskDecorator;

public final class UserContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        String captured = UserContextHolder.get();
        return () -> {
            if (captured != null) {
                UserContextHolder.set(captured);
            }
            try {
                runnable.run();
            } finally {
                UserContextHolder.clear();
            }
        };
    }
}
