package org.gemo.apex.platform.security;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.gemo.apex.platform.execution.UserContextTaskDecorator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UserContextPropagationTest {
    @AfterEach
    void clear() {
        UserContextHolder.clear();
    }

    /** taskDecorator应传播捕获值并在异步结束后清理 */
    @Test
    void taskDecoratorPropagatesCapturedValueAndClearsAfterAsyncCompletion() {
        UserContextHolder.set("user-1");
        AtomicReference<String> observed = new AtomicReference<>();
        Runnable decorated =
                new UserContextTaskDecorator()
                        .decorate(() -> observed.set(UserContextHolder.get()));
        UserContextHolder.clear();

        decorated.run();

        assertEquals("user-1", observed.get());
        assertNull(UserContextHolder.get());
    }
}
