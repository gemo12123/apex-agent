package org.gemo.apex.runtime.resource;

import java.util.*;
import java.util.concurrent.atomic.*;

public final class RuntimeResources implements AutoCloseable {
    private final List<AutoCloseable> owned;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RuntimeResources(List<AutoCloseable> o) {
        owned = List.copyOf(o);
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        RuntimeException failure = null;
        for (int i = owned.size() - 1; i >= 0; i--) {
            try {
                owned.get(i).close();
            } catch (Exception e) {
                if (failure == null) {
                    failure = new IllegalStateException("关闭 runtime 资源失败");
                }
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
