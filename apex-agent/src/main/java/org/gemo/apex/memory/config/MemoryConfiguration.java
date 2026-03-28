package org.gemo.apex.memory.config;

import org.gemo.apex.memory.persistence.repository.InMemoryMemoryManageRepository;
import org.gemo.apex.memory.persistence.repository.InMemoryMemoryReadRepository;
import org.gemo.apex.memory.persistence.repository.InMemoryMemoryWriteRepository;
import org.gemo.apex.memory.persistence.repository.JdbcMemoryManageRepository;
import org.gemo.apex.memory.persistence.repository.JdbcMemoryReadRepository;
import org.gemo.apex.memory.persistence.repository.JdbcMemoryWriteRepository;
import org.gemo.apex.memory.persistence.repository.MemoryManageRepository;
import org.gemo.apex.memory.persistence.repository.MemoryReadRepository;
import org.gemo.apex.memory.persistence.repository.MemoryWriteRepository;
import org.gemo.apex.memory.session.InMemorySessionContextStore;
import org.gemo.apex.memory.session.JdbcSessionContextStore;
import org.gemo.apex.memory.session.SessionContextStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 记忆系统基础配置。
 */
@Configuration
@EnableConfigurationProperties(MemoryProperties.class)
public class MemoryConfiguration {

    @Bean
    public ThreadPoolTaskScheduler memoryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("memory-task-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

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
    public MemoryReadRepository memoryReadRepository(MemoryProperties properties,
            InMemoryMemoryReadRepository inMemoryMemoryReadRepository,
            ObjectProvider<JdbcMemoryReadRepository> jdbcMemoryReadRepositoryProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore().getType())) {
            return jdbcMemoryReadRepositoryProvider.getIfAvailable();
        }
        return inMemoryMemoryReadRepository;
    }

    @Bean
    @Primary
    public MemoryWriteRepository memoryWriteRepository(MemoryProperties properties,
            InMemoryMemoryWriteRepository inMemoryMemoryWriteRepository,
            ObjectProvider<JdbcMemoryWriteRepository> jdbcMemoryWriteRepositoryProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore().getType())) {
            return jdbcMemoryWriteRepositoryProvider.getIfAvailable();
        }
        return inMemoryMemoryWriteRepository;
    }

    @Bean
    @Primary
    public MemoryManageRepository memoryManageRepository(MemoryProperties properties,
            InMemoryMemoryManageRepository inMemoryMemoryManageRepository,
            ObjectProvider<JdbcMemoryManageRepository> jdbcMemoryManageRepositoryProvider) {
        if ("jdbc".equalsIgnoreCase(properties.getStore().getType())) {
            return jdbcMemoryManageRepositoryProvider.getIfAvailable();
        }
        return inMemoryMemoryManageRepository;
    }
}
