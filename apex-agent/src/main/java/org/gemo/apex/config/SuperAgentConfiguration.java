package org.gemo.apex.config;

import org.gemo.apex.component.CustomToolCallingManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SuperAgent 的核心支撑配置
 * 提供对 ChatClient / ChatModel 的统一管理，同时供后续引擎获取配置。
 */
@Configuration
public class SuperAgentConfiguration {

    // 默认通过 Spring AI 的 starter 机制自动注入了 ChatModel
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .build();
    }

    @Bean
    public ToolCallingManager toolCallingManager(){
        return CustomToolCallingManager.builder().build();
    }
}
