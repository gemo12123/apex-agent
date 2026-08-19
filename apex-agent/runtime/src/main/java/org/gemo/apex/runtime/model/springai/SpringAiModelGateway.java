package org.gemo.apex.runtime.model.springai;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.gemo.apex.common.exception.CancellationRequestedException;
import org.gemo.apex.common.model.*;
import org.gemo.apex.common.tool.*;
import org.gemo.apex.extension.model.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.Disposable;

public final class SpringAiModelGateway implements ModelGateway {
    private final ChatModel model;
    private final SpringAiMessageMapper mapper = new SpringAiMessageMapper();

    public SpringAiModelGateway(ChatModel m) {
        model = Objects.requireNonNull(m);
    }

    public ModelResponse stream(ModelRequest r, ModelStreamObserver o) {
        o.cancellationToken().throwIfCancellationRequested();
        List<Message> ms = new ArrayList<>();
        ms.add(new SystemMessage(r.systemPrompt()));
        ms.addAll(r.prefixDeveloperMessages().stream().map(mapper::toSpring).toList());
        ms.addAll(r.messages().stream().map(mapper::toSpring).toList());
        var callbacks =
                r.tools().stream()
                        .map(
                                t ->
                                        (ToolCallback)
                                                new ToolCallback() {
                                                    public ToolDefinition getToolDefinition() {
                                                        return new DefaultToolDefinition(
                                                                t.name(),
                                                                t.description(),
                                                                t.inputSchemaJson());
                                                    }

                                                    public String call(String input) {
                                                        throw new IllegalStateException(
                                                                "Spring AI 自动工具执行已关闭");
                                                    }
                                                })
                        .toList();
        var options =
                ToolCallingChatOptions.builder()
                        .toolCallbacks(callbacks)
                        .internalToolExecutionEnabled(false)
                        .build();
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Disposable sub =
                model.stream(new Prompt(ms, options))
                        .subscribe(
                                x -> {
                                    Generation result = x.getResult();
                                    if (result == null) {
                                        return;
                                    }
                                    var out = result.getOutput();
                                    if (out.getText() != null) {
                                        text.append(out.getText());
                                    }
                                    int base = calls.size();
                                    for (int i = 0; i < out.getToolCalls().size(); i++) {
                                        calls.add(
                                                mapper.fromSpring(
                                                        out.getToolCalls().get(i), base + i));
                                    }
                                    o.onChunk(
                                            new ModelStreamChunk(
                                                    out.getText(),
                                                    calls.subList(base, calls.size()),
                                                    Map.of(),
                                                    false));
                                },
                                x -> {
                                    failure.set(x);
                                    done.countDown();
                                },
                                done::countDown);
        try (var reg =
                o.cancellationToken()
                        .onCancel(
                                () -> {
                                    sub.dispose();
                                    done.countDown();
                                })) {
            try {
                done.await();
            } catch (InterruptedException x) {
                Thread.currentThread().interrupt();
                sub.dispose();
                throw new CancellationRequestedException();
            }
        }
        if (o.cancellationToken().isCancellationRequested()) {
            throw new CancellationRequestedException();
        }
        if (failure.get() != null) {
            throw new IllegalStateException("模型调用失败", failure.get());
        }
        return new ModelResponse(text.toString(), calls, Map.of());
    }
}
