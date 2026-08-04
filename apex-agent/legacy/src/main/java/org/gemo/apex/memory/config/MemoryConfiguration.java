package org.gemo.apex.memory.config;

import org.gemo.apex.memory.session.InMemorySessionContextStore;
import org.gemo.apex.memory.session.JdbcSessionContextStore;
import org.gemo.apex.memory.session.SessionContextStore;
import org.gemo.apex.hook.lifecycle.AgentExecutionStore;
import org.gemo.apex.hook.lifecycle.InMemoryAgentExecutionStore;
import org.gemo.apex.hook.lifecycle.JdbcAgentExecutionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 记忆系统基础配置。
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {

    @Bean
    @Primary
    public SessionContextStore sessionContextStore(MemoryProperties properties,
            InMemorySessionContextStore inMemorySessionContextStore,
            ObjectProvider<JdbcSessionContextStore> jdbcSessionContextStoreProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore().getType())) {
            return jdbcSessionContextStoreProvider.getIfAvailable();
        }
        return inMemorySessionContextStore;
    }

    @Bean
    @Primary
    public AgentExecutionStore agentExecutionStore(MemoryProperties properties,
            InMemoryAgentExecutionStore inMemoryAgentExecutionStore,
            ObjectProvider<JdbcAgentExecutionStore> jdbcAgentExecutionStoreProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore().getType())) {
            return jdbcAgentExecutionStoreProvider.getIfAvailable();
        }
        return inMemoryAgentExecutionStore;
    }

}
