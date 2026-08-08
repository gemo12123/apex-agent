package org.gemo.apex.runtime.execution;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.gemo.apex.common.tool.*;

public final class RuntimeCancellationSource {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Set<R> rs = ConcurrentHashMap.newKeySet();
    private final CancellationToken token =
            new CancellationToken() {
                public boolean isCancellationRequested() {
                    return cancelled.get();
                }

                public CancellationRegistration onCancel(Runnable c) {
                    var r = new R(c);
                    rs.add(r);
                    if (cancelled.get()) {
                        r.run();
                    }
                    return () -> {
                        r.close();
                        rs.remove(r);
                    };
                }
            };

    public CancellationToken token() {
        return token;
    }

    public boolean cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return false;
        }
        rs.forEach(R::run);
        return true;
    }

    private static final class R {
        final Runnable c;
        final AtomicBoolean on = new AtomicBoolean(true);

        R(Runnable c) {
            this.c = Objects.requireNonNull(c);
        }

        void run() {
            if (on.compareAndSet(true, false)) {
                c.run();
            }
        }

        void close() {
            on.set(false);
        }
    }
}
