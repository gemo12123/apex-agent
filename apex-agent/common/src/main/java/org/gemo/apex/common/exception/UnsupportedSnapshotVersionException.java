package org.gemo.apex.common.exception;

public final class UnsupportedSnapshotVersionException extends RuntimeException {
    public UnsupportedSnapshotVersionException(String version) {
        super("不支持的快照版本: " + version);
    }
}
