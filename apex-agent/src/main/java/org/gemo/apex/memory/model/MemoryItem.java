package org.gemo.apex.memory.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 逻辑层统一记忆对象。
 */
@Data
public class MemoryItem {

    /**
     * 记忆主键。
     */
    private String id;

    /**
     * 用户标识。
     */
    private String userId;

    /**
     * 智能体标识。
     */
    private String agentKey;

    /**
     * 记忆类型。
     */
    private MemoryType type;

    /**
     * 用户画像分类。
     */
    private MemoryCategory memoryCategory;

    /**
     * 归并键。
     */
    private String memoryKey;

    /**
     * 主题键。
     */
    private String topicKey;

    /**
     * 记忆标题。
     */
    private String title;

    /**
     * 记忆内容。
     */
    private String content;

    /**
     * 结构化负载。
     */
    private String structuredPayload;

    /**
     * 置信度。
     */
    private Double confidence;

    /**
     * 重要度。
     */
    private Integer importance;

    /**
     * 执行历史时间层级。
     */
    private ExecutionTimeScope timeScope;

    /**
     * 记忆状态。
     */
    private MemoryStatus status = MemoryStatus.ACTIVE;

    /**
     * 观察时间。
     */
    private LocalDateTime observedTime;

    /**
     * 失效时间。
     */
    private LocalDateTime expireTime;

    /**
     * 来源会话。
     */
    private String sourceSessionId;

    /**
     * 执行历史开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 执行历史结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 最近写入轮次。
     */
    private Integer turnNo;

    /**
     * 乐观锁版本。
     */
    private Long versionNo;
}
