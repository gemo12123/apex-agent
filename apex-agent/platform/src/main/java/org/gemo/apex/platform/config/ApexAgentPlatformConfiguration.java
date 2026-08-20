package org.gemo.apex.platform.config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import org.gemo.apex.extension.definition.AgentDefinitionProvider;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.extension.model.ModelGateway;
import org.gemo.apex.extension.repository.ConversationRepository;
import org.gemo.apex.extension.repository.SessionRepository;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.kit.hook.AskHumanInterventionHook;
import org.gemo.apex.kit.hook.AvailableSkillsPromptHook;
import org.gemo.apex.kit.hook.PlainTextTruncateHook;
import org.gemo.apex.kit.hook.SkillActivationStateHook;
import org.gemo.apex.kit.hook.TodoMiddleware;
import org.gemo.apex.kit.hook.ToolConfirmHook;
import org.gemo.apex.kit.hook.ToolResultTruncateHook;
import org.gemo.apex.kit.tool.ActivateSkillTool;
import org.gemo.apex.kit.tool.AskHumanTool;
import org.gemo.apex.kit.tool.WriteTodosTool;
import org.gemo.apex.platform.execution.UserContextTaskDecorator;
import org.gemo.apex.platform.skill.RuntimeSkillRegistry;
import org.gemo.apex.platform.web.sse.RequestBoundAgentEventPublisherFactory;
import org.gemo.apex.runtime.api.ApexAgentRuntime;
import org.gemo.apex.runtime.skill.FileSkillProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 将 Spring Bean 装配为独立于 IoC 的 {@link ApexAgentRuntime}，并配置执行线程池。 */
@Configuration
@EnableConfigurationProperties(ApexAgentPlatformProperties.class)
public class ApexAgentPlatformConfiguration {
    private static final String RUNTIME_SKILL_REGISTRY_BEAN = "runtimeSkillRegistry";

    @Bean
    AgentDefinitionProvider agentDefinitionProvider(
            ApexAgentPlatformProperties properties, ResourceLoader resourceLoader) {
        return new SpringPropertiesAgentDefinitionProvider(properties, resourceLoader);
    }

    @Bean
    RequestBoundAgentEventPublisherFactory requestPublisherFactory() {
        return new RequestBoundAgentEventPublisherFactory();
    }

    /** 聚合所有原始 SkillProvider，并作为 platform 对外提供的最终 SkillProvider。 */
    @Bean(name = RUNTIME_SKILL_REGISTRY_BEAN)
    @Primary
    RuntimeSkillRegistry runtimeSkillRegistry(
            ApexAgentPlatformProperties properties,
            ObjectProvider<SkillProvider> skillProviders,
            ListableBeanFactory beanFactory) {
        boolean hasSourceProvider =
                Arrays.stream(beanFactory.getBeanNamesForType(SkillProvider.class))
                        .anyMatch(name -> !RUNTIME_SKILL_REGISTRY_BEAN.equals(name));
        List<SkillProvider> providers =
                hasSourceProvider
                        ? skillProviders.orderedStream().toList()
                        : List.of(new FileSkillProvider(properties.getSkills().getPath()));
        return new RuntimeSkillRegistry(providers);
    }

    @Bean
    AvailableSkillsPromptHook availableSkillsPromptHook(RuntimeSkillRegistry skillRegistry) {
        return new AvailableSkillsPromptHook(skillRegistry);
    }

    /** 收集平台提供的端口、工具、Hook 与 Skill。模型 Bean 必须唯一，避免运行时隐式选择模型。 */
    @Bean(destroyMethod = "close")
    ApexAgentRuntime apexAgentRuntime(
            AgentDefinitionProvider definitions,
            SessionRepository sessions,
            ConversationRepository conversations,
            RequestBoundAgentEventPublisherFactory publishers,
            ObjectProvider<ModelGateway> gateways,
            ObjectProvider<ChatModel> chatModels,
            ObjectProvider<ToolCallingManager> toolCallingManagers,
            ObjectProvider<AgentTool> tools,
            ObjectProvider<ToolCallback> toolCallbacks,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
            ObjectProvider<LifecycleHook<?, ?>> hooks,
            RuntimeSkillRegistry skillRegistry) {
        var builder =
                ApexAgentRuntime.builder()
                        .agentDefinitionProvider(definitions)
                        .sessionRepository(sessions)
                        .conversationRepository(conversations)
                        .defaultEventPublisherFactory(publishers);
        ModelGateway gateway = gateways.getIfUnique();
        ChatModel chatModel = chatModels.getIfUnique();
        if (gateway != null) {
            builder.modelGateway(gateway);
        } else if (chatModel != null) {
            builder.chatModel(chatModel);
        } else {
            throw new IllegalStateException("platform 启动需要唯一 ModelGateway 或 ChatModel Bean");
        }
        tools.orderedStream().forEach(builder::registerTool);
        ToolCallingManager toolCallingManager = toolCallingManagers.getIfUnique();
        if (toolCallingManager != null) {
            builder.toolCallingManager(toolCallingManager);
        }
        toolCallbacks.orderedStream().forEach(builder::registerToolCallback);
        toolCallbackProviders.orderedStream().forEach(builder::registerToolCallbackProvider);
        hooks.orderedStream().forEach(builder::registerHook);
        builder.skillProvider(skillRegistry);
        return builder.build();
    }

    /** 创建承载 Agent 的线程池，并通过 decorator 传播请求用户上下文。 */
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

    @Bean
    ToolConfirmHook toolConfirmHook() {
        return new ToolConfirmHook();
    }

    /** 内置工具：激活 Skill 并返回其指令，路由到最终 Skill 注册表。 */
    @Bean
    ActivateSkillTool activateSkillTool(RuntimeSkillRegistry skillRegistry) {
        return new ActivateSkillTool(skillRegistry);
    }

    /** 内置工具：挂起任务并向用户提问。 */
    @Bean
    AskHumanTool askHumanTool() {
        return new AskHumanTool();
    }

    @Bean
    WriteTodosTool writeTodosTool() {
        return new WriteTodosTool();
    }

    @Bean
    TodoMiddleware todoMiddleware() {
        return new TodoMiddleware();
    }

    /** 内置交互 Hook：ask_human 预调用时挂起并请求用户输入。 */
    @Bean
    AskHumanInterventionHook askHumanInterventionHook() {
        return new AskHumanInterventionHook();
    }

    /** 内置 Hook：截断过长文本工具结果。 */
    @Bean
    PlainTextTruncateHook plainTextTruncateHook() {
        return new PlainTextTruncateHook();
    }

    /** 内置 Hook：按预算自适应截断工具结果并落盘完整原文。 */
    @Bean
    ToolResultTruncateHook toolResultTruncateHook() {
        return new ToolResultTruncateHook();
    }

    /** 内置 Hook：从 activate_skill 结果提取 Skill 激活状态增量。 */
    @Bean
    SkillActivationStateHook skillActivationStateHook() {
        return new SkillActivationStateHook();
    }

}
