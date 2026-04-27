package org.gemo.apex.memory.search;

import org.gemo.apex.memory.config.MemoryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SpringAiMemoryEmbeddingServiceTest {

    @Test
    void embedShouldReturnNullWhenTextIsBelowConfiguredThreshold() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        MemoryProperties properties = new MemoryProperties();
        properties.getSearch().setMinEmbeddingTextLength(8);
        SpringAiMemoryEmbeddingService service = new SpringAiMemoryEmbeddingService(model, properties);

        assertNull(service.embed("short"));
        verifyNoInteractions(model);
    }

    @Test
    void embedShouldDelegateToEmbeddingModelWhenEligible() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        MemoryProperties properties = new MemoryProperties();
        properties.getSearch().setMinEmbeddingTextLength(8);
        when(model.embed("eligible text")).thenReturn(new float[] {0.1f, 0.2f});

        SpringAiMemoryEmbeddingService service = new SpringAiMemoryEmbeddingService(model, properties);

        assertArrayEquals(new float[] {0.1f, 0.2f}, service.embed("eligible text"));
        verify(model).embed("eligible text");
    }
}
