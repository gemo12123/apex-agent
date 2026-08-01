package org.gemo.apex.common.tool;

import org.gemo.apex.common.exception.CancellationRequestedException;

public interface CancellationToken {
    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new CancellationRequestedException();
        }
    }

    CancellationRegistration onCancel(Runnable command);
}
