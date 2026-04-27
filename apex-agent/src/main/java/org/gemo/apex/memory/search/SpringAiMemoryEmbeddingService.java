package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.springframework.ai.embedding.EmbeddingModel;

public class SpringAiMemoryEmbeddingService implements MemoryEmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final MemoryProperties memoryProperties;

    public SpringAiMemoryEmbeddingService(EmbeddingModel embeddingModel, MemoryProperties memoryProperties) {
        this.embeddingModel = embeddingModel;
        this.memoryProperties = memoryProperties;
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (text.length() < memoryProperties.getSearch().getMinEmbeddingTextLength()) {
            return null;
        }
        return embeddingModel.embed(text);
    }
}
