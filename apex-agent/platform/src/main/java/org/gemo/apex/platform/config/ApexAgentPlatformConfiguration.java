package org.gemo.apex.platform.config;

import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.platform.execution.UserContextTaskDecorator;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(ApexAgentPlatformProperties.class)
public class ApexAgentPlatformConfiguration {
    @Bean
    AgentDefinitionProvider agentDefinitionProvider(ApexAgentPlatformProperties properties,
                                                    ResourceLoader resourceLoader) {
        return new SpringPropertiesAgentDefinitionProvider(properties, resourceLoader);
    }

    @Bean
    RequestBoundAgentEventPublisherFactory requestPublisherFactory() {
        return new RequestBoundAgentEventPublisherFactory();
    }

    @Bean(destroyMethod = "close")
    ApexAgentRuntime apexAgentRuntime(AgentDefinitionProvider definitions, SessionRepository sessions,
                                      ConversationRepository conversations,
                                      RequestBoundAgentEventPublisherFactory publishers,
                                      ObjectProvider<ModelGateway> gateways,
                                      ObjectProvider<ChatModel> chatModels,
                                      ObjectProvider<AgentTool> tools,
                                      ObjectProvider<PlatformHookRegistration> hooks,
                                      ObjectProvider<SkillDefinition> skills) {
        var builder = ApexAgentRuntime.builder().agentDefinitionProvider(definitions)
                .sessionRepository(sessions).conversationRepository(conversations)
                .defaultEventPublisherFactory(publishers);
        ModelGateway gateway = gateways.getIfUnique();
        ChatModel chatModel = chatModels.getIfUnique();
        if (gateway != null) builder.modelGateway(gateway);
        else if (chatModel != null) builder.chatModel(chatModel);
        else throw new IllegalStateException("platform 启动需要唯一 ModelGateway 或 ChatModel Bean");
        tools.orderedStream().forEach(builder::registerTool);
        hooks.orderedStream().forEach(value -> builder.registerHook(value.stableName(), value.hook()));
        skills.orderedStream().forEach(builder::registerSkill);
        return builder.build();
    }

    @Bean(name = "agentExecutionExecutor")
    Executor agentExecutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("apex-agent-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(new UserContextTaskDecorator());
        executor.initialize();
        return executor;
    }
}
