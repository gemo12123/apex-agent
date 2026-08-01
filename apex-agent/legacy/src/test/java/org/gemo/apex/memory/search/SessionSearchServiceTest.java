package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionSearchServiceTest {

    @Test
    void searchShouldMergeAndDeduplicateRepositoryHits() {
        SessionSearchRepository repository = mock(SessionSearchRepository.class);
        MemoryEmbeddingService embeddingService = mock(MemoryEmbeddingService.class);
        MemoryProperties properties = new MemoryProperties();
        properties.getStore().setType("jdbc");
        when(embeddingService.embed("find previous fix")).thenReturn(new float[] {0.1f, 0.2f});
        when(repository.search(any(), any(), any())).thenReturn(List.of(
                new SessionSearchHit("dialogue_message", "session-1", "message-1", 2, 3L, "user", 0.91,
                        new SessionSearchHit.ScoreBreakdown(0.8, 0.95, 0.91), "snippet", null),
                new SessionSearchHit("dialogue_message", "session-1", "message-1", 2, 3L, "user", 0.90,
                        new SessionSearchHit.ScoreBreakdown(0.75, 0.94, 0.90), "snippet", null)));

        SessionSearchResult result = new SessionSearchService(repository, embeddingService, properties)
                .search(new SessionSearchQuery("find previous fix", null, 8, "hybrid", true, true),
                        new SessionSearchScope("user-1", "agent-1"));

        assertEquals(1, result.getHits().size());
        assertEquals("message-1", result.getHits().getFirst().getMessageId());
    }
}
