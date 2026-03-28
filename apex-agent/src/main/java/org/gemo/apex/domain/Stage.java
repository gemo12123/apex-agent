package org.gemo.apex.domain;

import lombok.Data;
import org.gemo.apex.constant.TaskStatus;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 计划阶段定义
 */
@Data
public class Stage {
    private String id = UUID.randomUUID().toString();

    private String name;

    private String description;

    private List<String> skills;

    private String expectedOutput;

    private TaskStatus status = TaskStatus.PENDING;

    private List<String> result = new ArrayList<>();

    private List<FileMetadata> attachList = new ArrayList<>();

    private List<Message> conversationHistory = new ArrayList<>();

    private Map<String, Object> pendingToolResult;
}
