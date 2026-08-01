package org.gemo.apex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Primary;

/**
 * OpenAI (或兼容的大模型接口如 DashScope) 配置属性
 * 从 application.yml 中读取 spring.ai.openai 相关配置
 */
@Data
@Primary
@ConfigurationProperties(prefix = "spring.ai.openai")
public class OpenAIProperties {

    /**
     * API 密钥
     */
    private String apiKey;

    /**
     * 基础 URL
     */
    private String baseUrl;

    /**
     * 聊天配置
     */
    private ChatConfig chat = new ChatConfig();

    @Data
    public static class ChatConfig {
        /**
         * 聊天选项
         */
        private Options options = new Options();

        @Data
        public static class Options {
            /**
             * 模型名称
             */
            private String model;

            /**
             * 温度参数
             */
            private Double temperature;
        }
    }
}
