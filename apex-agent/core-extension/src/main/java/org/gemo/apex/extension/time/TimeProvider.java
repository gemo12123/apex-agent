package org.gemo.apex.extension.time;

import java.time.Instant;

public interface TimeProvider {
    Instant now();
}
