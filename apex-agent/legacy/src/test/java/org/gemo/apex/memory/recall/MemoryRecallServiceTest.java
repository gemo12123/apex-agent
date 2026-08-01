package org.gemo.apex.memory.recall;

import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.config.MemoryConfigService;
import org.gemo.apex.memory.config.MemoryProperties;
import org.gemo.apex.memory.model.MemoryItem;
import org.gemo.apex.memory.model.MemoryRecallPackage;
import org.gemo.apex.memory.persistence.repository.MemoryReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MemoryRecallServiceTest {

    @Mock
    private MemoryReadRepository memoryReadRepository;

    private MemoryRecallService memoryRecallService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        MemoryProperties memoryProperties = new MemoryProperties();
        memoryProperties.setEnabled(true);
        memoryProperties.setProfileEnabled(true);
        memoryProperties.setExperienceEnabled(true);
        memoryProperties.setExecutionHistoryEnabled(true);
        memoryRecallService = new MemoryRecallService(memoryReadRepository, new MemoryConfigService(memoryProperties));
    }

    @Test
    void recallShouldLoadOnlyProfileAndExperienceItems() {
        SuperAgentContext context = new SuperAgentContext();
        context.setUserId("user-1");
        context.setAgentKey("agent-1");
        context.setSessionId("session-1");
        when(memoryReadRepository.findProfileItems("user-1", "agent-1", 5))
                .thenReturn(List.of(memoryItem("画像", "偏好咖啡")));
        when(memoryReadRepository.findExperienceItems("agent-1", 5))
                .thenReturn(List.of(memoryItem("经验", "优先展示澄清选项")));

        MemoryRecallPackage recallPackage = memoryRecallService.recall(context);

        assertEquals(1, recallPackage.getProfileItems().size());
        assertEquals(1, recallPackage.getExperienceItems().size());
        verify(memoryReadRepository).findProfileItems("user-1", "agent-1", 5);
        verify(memoryReadRepository).findExperienceItems("agent-1", 5);
        verifyNoMoreInteractions(memoryReadRepository);
    }

    private MemoryItem memoryItem(String title, String content) {
        MemoryItem item = new MemoryItem();
        item.setTitle(title);
        item.setContent(content);
        return item;
    }
}
