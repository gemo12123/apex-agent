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
     * 存储配置。
     */
    private StoreProperties store = new StoreProperties();

    /**
     * 压缩配置。
     */
    private CompactionProperties compaction = new CompactionProperties();

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

    }
}
