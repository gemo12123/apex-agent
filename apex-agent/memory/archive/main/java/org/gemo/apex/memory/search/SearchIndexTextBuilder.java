package org.gemo.apex.memory.search;

import org.gemo.apex.memory.model.MemoryItem;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class SearchIndexTextBuilder {

    public String buildDialogueMessageText(Message message, String content, String toolName) {
        return normalize(joinNonBlank(content, toolName));
    }

    public String buildSummaryText(String content) {
        return normalize(content);
    }

    public String buildExecutionHistoryText(MemoryItem item) {
        if (item == null) {
            return "";
        }
        return normalize(joinNonBlank(item.getTitle(), item.getContent(), item.getTopicKey(), item.getStructuredPayload()));
    }

    private String joinNonBlank(String... parts) {
        return Arrays.stream(parts)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("\n"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").trim();
    }
}
