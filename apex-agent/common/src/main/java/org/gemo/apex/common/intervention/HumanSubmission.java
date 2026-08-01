package org.gemo.apex.common.intervention;

public sealed interface HumanSubmission permits QuestionSubmission, ToolConfirmationSubmission {
    String toolCallId();
}
