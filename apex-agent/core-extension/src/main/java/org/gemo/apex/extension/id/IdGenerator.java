package org.gemo.apex.extension.id;

public interface IdGenerator {
    String newExecutionId();

    String newEntryId();

    String newInvocationId();

    String newConfirmationId();

    String newSubSessionId();

    String newCompactionId();
}
