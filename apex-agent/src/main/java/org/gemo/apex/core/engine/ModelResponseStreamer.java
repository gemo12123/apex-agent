package org.gemo.apex.core.engine;

import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ChangeType;
import org.gemo.apex.constant.ContextKeyEnum;
import org.gemo.apex.constant.ModeEnum;
import org.gemo.apex.constant.TaskStatus;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.message.StreamContentMessage;
import org.gemo.apex.message.StreamThinkMessage;
import org.gemo.apex.message.TaskThinkChangeMessage;
import org.gemo.apex.util.MessageUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class ModelResponseStreamer {

    private final ChatModel chatModel;

    public ModelResponseStreamer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ChatResponse stream(Prompt prompt, SuperAgentContext context) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> finalResponseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StringBuilder fullContent = new StringBuilder();
        String interactionId = UUID.randomUUID().toString();

        chatModel.stream(prompt).subscribe(response -> {
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return;
            }

            AssistantMessage output = response.getResult().getOutput();
            if (EngineContextHelper.hasToolCalls(output)) {
                finalResponseRef.set(response);
                return;
            }
            if (output.getText() == null || output.getText().isEmpty()) {
                return;
            }

            fullContent.append(output.getText());
            if (context.getCurrentStage() == SuperAgentContext.Stage.THINKING) {
                MessageUtils.sendMessage(context, StreamThinkMessage.builder()
                        .context(EngineContextHelper.buildMessageContext(context,
                                ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                        .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                        .build());
            } else if (context.getCurrentStage() == SuperAgentContext.Stage.EXECUTION) {
                if (context.getExecutionMode() == ModeEnum.PLAN_EXECUTOR && context.getPlan() != null) {
                    boolean allCompleted = context.getPlan().getStages() != null
                            && context.getPlan().getStages().stream()
                            .allMatch(stage -> stage.getStatus() == TaskStatus.COMPLETED);
                    if (!allCompleted && context.getCurrentStageId() != null) {
                        MessageUtils.sendMessage(context, TaskThinkChangeMessage.builder()
                                .context(EngineContextHelper.buildMessageContext(context,
                                        ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                                .messages(List.of(TaskThinkChangeMessage.TaskThinkChangeDetail.builder()
                                        .taskId(context.getCurrentStageId())
                                        .changeType(ChangeType.CONTENT_APPEND.name())
                                        .content(output.getText())
                                        .build()))
                                .build());
                    } else {
                        MessageUtils.sendMessage(context, StreamContentMessage.builder()
                                .context(EngineContextHelper.buildMessageContext(context,
                                        ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                                .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                                .build());
                    }
                } else if (context.getExecutionMode() == ModeEnum.REACT) {
                    MessageUtils.sendMessage(context, StreamContentMessage.builder()
                            .context(EngineContextHelper.buildMessageContext(context,
                                    ContextKeyEnum.CONTENT_ID.getKey(), interactionId))
                            .messages(List.of(new StreamThinkMessage.ContentMessage(output.getText())))
                            .build());
                }
            }
        }, error -> {
            log.error("Stream model response failed: {}", error.getMessage(), error);
            errorRef.set(error);
            latch.countDown();
        }, () -> {
            if (finalResponseRef.get() == null && fullContent.length() > 0) {
                AssistantMessage finalMsg = new AssistantMessage(fullContent.toString());
                finalResponseRef.set(new ChatResponse(List.of(new Generation(finalMsg))));
            }
            latch.countDown();
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for model response", e);
        }

        if (errorRef.get() != null) {
            throw new IllegalStateException("Stream model response failed", errorRef.get());
        }
        if (finalResponseRef.get() == null) {
            throw new IllegalStateException("Model returned neither content nor tool calls");
        }
        return finalResponseRef.get();
    }
}
