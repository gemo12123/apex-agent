package org.gemo.apex.memory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 记忆系统配置。
 */
@Data
@ConfigurationProperties(prefix = "apex.memory")
public class MemoryProperties {

    /**
     * 记忆系统总开关。
     */
    private boolean enabled = true;

    /**
     * 用户画像记忆开关。
     */
    private boolean profileEnabled = true;

    /**
     * 执行历史记忆开关。
     */
    private boolean executionHistoryEnabled = true;

    /**
     * 智能体经验记忆开关。
     */
    private boolean experienceEnabled = true;

    /**
     * 记忆管理接口开关。
     */
    private boolean managementEnabled = true;

    /**
     * 存储配置。
     */
    private StoreProperties store = new StoreProperties();

    /**
     * 压缩配置。
     */
    private CompactionProperties compaction = new CompactionProperties();

    /**
     * 记忆抽取配置。
     */
    private ExtractionProperties extraction = new ExtractionProperties();

    @Data
    public static class StoreProperties {

        /**
         * 存储类型，支持 in-memory / jdbc。
         */
        private String type = "in-memory";
    }

    @Data
    public static class CompactionProperties {

        /**
         * 会话压缩开关。
         */
        private boolean enabled = true;

        /**
         * 非固定消息压缩阈值。
         */
        private int tokenThreshold = 12000;

        /**
         * 压缩后保留的最近对话消息数。
         */
        private int retainRecentMessages = 12;

        /**
         * 压缩前是否先执行记忆刷新。
         */
        private boolean preflushEnabled = true;
    }

    @Data
    public static class ExtractionProperties {

        /**
         * 是否启用异步抽取。
         */
        private boolean asyncEnabled = true;

        /**
         * 候选记忆最小置信度。
         */
        private double minConfidence = 0.65D;

        /**
         * 长期记忆静默分钟数。
         */
        private int longTermIdleMinutes = 15;

        /**
         * 长期记忆提示词路径。
         */
        private String longTermPrompt;

        /**
         * 执行历史提示词路径。
         */
        private String executionHistoryPrompt;

        /**
         * 智能体经验提示词路径。
         */
        private String experiencePrompt;

        /**
         * 是否启用版本防覆盖。
         */
        private boolean optimisticLockEnabled = true;
    }
}
