package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.tool.CancellationRegistration;
import org.gemo.apex.common.tool.CancellationToken;
import org.junit.jupiter.api.Test;

class CancellationTokenContractTest {
    /** 取消前注册应执行一次且close可注销 */
    @Test
    void executesPreCancellationRegistrationOnceAndAllowsCloseToUnregister() {
        TestCancellationSource source = new TestCancellationSource();
        AtomicInteger called = new AtomicInteger();
        source.token().onCancel(called::incrementAndGet);
        CancellationRegistration removed = source.token().onCancel(called::incrementAndGet);
        removed.close();

        assertTrue(source.cancel());
        assertFalse(source.cancel());
        assertEquals(1, called.get());
        assertThrows(
                CancellationRequestedException.class, source.token()::throwIfCancellationRequested);
    }

    /** 取消后注册应立即执行 */
    @Test
    void executesPostCancellationRegistrationImmediately() {
        TestCancellationSource source = new TestCancellationSource();
        source.cancel();
        AtomicInteger called = new AtomicInteger();

        source.token().onCancel(called::incrementAndGet).close();

        assertEquals(1, called.get());
    }

    /** 并发注册和取消的回调至多执行一次 */
    @Test
    void executesConcurrentRegistrationAndCancellationCallbacksAtMostOnce()
            throws InterruptedException {
        for (int attempt = 0; attempt < 100; attempt++) {
            TestCancellationSource source = new TestCancellationSource();
            AtomicInteger called = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            Thread register =
                    Thread.ofPlatform()
                            .start(
                                    () -> {
                                        await(start);
                                        source.token().onCancel(called::incrementAndGet);
                                    });
            Thread cancel =
                    Thread.ofPlatform()
                            .start(
                                    () -> {
                                        await(start);
                                        source.cancel();
                                    });
            start.countDown();
            register.join();
            cancel.join();
            assertEquals(1, called.get());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static final class TestCancellationSource {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicInteger ids = new AtomicInteger();
        private final Map<Integer, Runnable> commands = new ConcurrentHashMap<>();
        private final CancellationToken token =
                new CancellationToken() {
                    @Override
                    public boolean isCancellationRequested() {
                        return cancelled.get();
                    }

                    @Override
                    public CancellationRegistration onCancel(Runnable command) {
                        if (cancelled.get()) {
                            command.run();
                            return () -> {};
                        }
                        int id = ids.incrementAndGet();
                        commands.put(id, command);
                        if (cancelled.get() && commands.remove(id, command)) {
                            command.run();
                        }
                        return () -> commands.remove(id, command);
                    }
                };

        CancellationToken token() {
            return token;
        }

        boolean cancel() {
            if (!cancelled.compareAndSet(false, true)) {
                return false;
            }
            commands.forEach(
                    (id, command) -> {
                        if (commands.remove(id, command)) {
                            command.run();
                        }
                    });
            return true;
        }
    }
}
