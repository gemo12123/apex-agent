package org.gemo.apex.common.exception;

public final class SnapshotDecodingException extends RuntimeException {
    public SnapshotDecodingException(String sessionId, String version, Throwable cause) {
        super("SessionSnapshot 解码失败，sessionId=" + sessionId + ", schemaVersion=" + version, cause);
    }
}
