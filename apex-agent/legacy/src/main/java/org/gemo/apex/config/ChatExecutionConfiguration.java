package org.gemo.apex.config;

import org.gemo.apex.memory.context.UserContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ChatExecutionConfiguration {

    @Bean(name = "chatStreamExecutor")
    public ThreadPoolTaskExecutor chatStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("chat-stream-");
        executor.setTaskDecorator(userContextTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskDecorator userContextTaskDecorator() {
        return runnable -> {
            String userId = UserContextHolder.getUserId();
            return () -> {
                try {
                    UserContextHolder.setUserId(userId);
                    runnable.run();
                } finally {
                    UserContextHolder.clear();
                }
            };
        };
    }
}
