package org.gemo.apex.common.tool;

@FunctionalInterface
public interface CancellationRegistration extends AutoCloseable {
    @Override
    void close();
}
