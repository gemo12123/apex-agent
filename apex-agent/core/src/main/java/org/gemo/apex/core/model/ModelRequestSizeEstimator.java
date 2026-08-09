package org.gemo.apex.core.model;

import org.gemo.apex.common.model.ModelRequest;

/** 以统一规则估算模型请求各组成部分的字符数和 token 数。 */
public final class ModelRequestSizeEstimator {
    public Size estimate(ModelRequest request) {
        long messageCharacters =
                request.messages().stream()
                        .mapToLong(
                                message ->
                                        (message.content() == null ? 0 : message.content().length())
                                                + message.payload().toString().length())
                        .sum();
        long systemCharacters = request.systemPrompt().length();
        long toolCharacters =
                request.tools().stream()
                        .mapToLong(
                                tool ->
                                        tool.description().length()
                                                + tool.inputSchemaJson().length())
                        .sum();
        return new Size(
                estimateTokens(messageCharacters),
                messageCharacters,
                estimateTokens(systemCharacters),
                systemCharacters,
                estimateTokens(toolCharacters),
                toolCharacters);
    }

    private long estimateTokens(long characters) {
        return (characters + 3) / 4;
    }

    public record Size(
            long messageTokens,
            long messageCharacters,
            long systemTokens,
            long systemCharacters,
            long toolTokens,
            long toolCharacters) {
        public long totalTokens() {
            return messageTokens + systemTokens + toolTokens;
        }

        public long totalCharacters() {
            return messageCharacters + systemCharacters + toolCharacters;
        }
    }
}
