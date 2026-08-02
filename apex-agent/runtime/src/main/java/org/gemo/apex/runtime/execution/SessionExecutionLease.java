package org.gemo.apex.runtime.execution;public interface SessionExecutionLease extends AutoCloseable{String sessionId();void release();default void close(){release();}}
