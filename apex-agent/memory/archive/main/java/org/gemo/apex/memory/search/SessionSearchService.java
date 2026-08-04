package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SessionSearchService {

    private final SessionSearchRepository sessionSearchRepository;
    private final MemoryEmbeddingService memoryEmbeddingService;
    private final MemoryProperties memoryProperties;

    public SessionSearchService(SessionSearchRepository sessionSearchRepository,
            MemoryEmbeddingService memoryEmbeddingService,
            MemoryProperties memoryProperties) {
        this.sessionSearchRepository = sessionSearchRepository;
        this.memoryEmbeddingService = memoryEmbeddingService;
        this.memoryProperties = memoryProperties;
    }

    public SessionSearchResult search(SessionSearchQuery query, SessionSearchScope scope) {
        if (query == null || query.getQuery() == null || query.getQuery().isBlank()) {
            return new SessionSearchResult(query != null ? query.getQuery() : null, List.of());
        }
        if (!"jdbc".equalsIgnoreCase(memoryProperties.getStore().getType())) {
            return new SessionSearchResult(query.getQuery(), List.of());
        }

        float[] queryEmbedding = requiresVector(query.getSearchMode()) ? memoryEmbeddingService.embed(query.getQuery()) : null;
        List<SessionSearchHit> hits = sessionSearchRepository.search(query, scope, queryEmbedding);
        return new SessionSearchResult(query.getQuery(), deduplicateAndSort(hits, normalizedLimit(query.getLimit())));
    }

    private boolean requiresVector(String searchMode) {
        if (searchMode == null) {
            return true;
        }
        String normalized = searchMode.toLowerCase(Locale.ROOT);
        return !"fts".equals(normalized);
    }

    private int normalizedLimit(Integer requestedLimit) {
        int defaultLimit = memoryProperties.getSearch().getDefaultSessionSearchLimit();
        int maxLimit = memoryProperties.getSearch().getMaxSessionSearchLimit();
        int effective = requestedLimit == null || requestedLimit <= 0 ? defaultLimit : requestedLimit;
        return Math.min(effective, maxLimit);
    }

    private List<SessionSearchHit> deduplicateAndSort(List<SessionSearchHit> hits, int limit) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        Map<String, SessionSearchHit> deduplicated = new LinkedHashMap<>();
        for (SessionSearchHit hit : hits) {
            if (hit == null) {
                continue;
            }
            String key = hit.getSourceType() + "::" + hit.getSessionId() + "::" + hit.getMessageId();
            SessionSearchHit existing = deduplicated.get(key);
            if (existing == null || hit.getScore() > existing.getScore()) {
                deduplicated.put(key, hit);
            }
        }
        List<SessionSearchHit> sorted = new ArrayList<>(deduplicated.values());
        sorted.sort(Comparator.comparingDouble(SessionSearchHit::getScore).reversed());
        return sorted.size() > limit ? new ArrayList<>(sorted.subList(0, limit)) : sorted;
    }
}
