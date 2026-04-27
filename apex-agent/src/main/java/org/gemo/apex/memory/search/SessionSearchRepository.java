package org.gemo.apex.memory.search;

import java.util.List;

public interface SessionSearchRepository {
    List<SessionSearchHit> search(SessionSearchQuery query, SessionSearchScope scope, float[] queryEmbedding);
}
