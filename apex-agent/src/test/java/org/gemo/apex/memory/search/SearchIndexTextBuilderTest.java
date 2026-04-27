package org.gemo.apex.memory.search;

import org.gemo.apex.memory.model.MemoryItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTextBuilderTest {

    @Test
    void buildExecutionHistoryTextShouldIncludeTitleContentTopicAndStructuredPayload() {
        MemoryItem item = new MemoryItem();
        item.setTitle("Fix recall");
        item.setContent("Use session search");
        item.setTopicKey("memory");
        item.setStructuredPayload("{\"result\":\"ok\"}");

        String searchText = new SearchIndexTextBuilder().buildExecutionHistoryText(item);

        assertTrue(searchText.contains("Fix recall"));
        assertTrue(searchText.contains("Use session search"));
        assertTrue(searchText.contains("memory"));
        assertTrue(searchText.contains("\"result\":\"ok\""));
    }
}
