package org.gemo.apex.common.exception;

public final class InvalidSnapshotException extends DomainInvariantException {
    public InvalidSnapshotException(String message) {
        super(message);
    }
}
