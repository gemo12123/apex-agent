package org.gemo.apex.component;

import cn.hutool.core.util.IdUtil;
import io.micrometer.observation.ObservationRegistry;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.tool.metadata.CustomToolMetadata;
import org.gemo.apex.util.ToolExecutionMessageHelper;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.observation.DefaultToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationContext;
import org.springframework.ai.tool.observation.ToolCallingObservationConvention;
import org.springframework.ai.tool.observation.ToolCallingObservationDocumentation;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 自定义工具调用管理器
 *
 * <p>
 * 这是 Spring AI 框架中 ToolCallingManager 接口的自定义实现，负责协调和管理 AI Agent 系统中的工具调用流程。
 *
 * <p>
 * <b>核心职责：</b>
 * <ol>
 * <li>解析工具定义 - 根据工具名称或 ToolCallback 对象解析出完整的工具定义列表</li>
 * <li>执行工具调用 - 接收 LLM 返回的工具调用请求，执行实际的工具方法并收集结果</li>
 * <li>管理对话历史 - 构建包含工具调用和工具响应的完整对话上下文，用于多轮对话</li>
 * <li>异常处理与监控 - 处理工具执行过程中的异常，并提供可观测性支持</li>
 * </ol>
 *
 * <p>
 * <b>典型工作流程：</b>
 * 
 * <pre>
 * 1. LLM 返回响应，包含工具调用请求（如 PlanTool、RePlanTool 等）
 * 2. executeToolCalls() 接收 Prompt 和 ChatResponse
 * 3. 解析要调用的工具，查找对应的 ToolCallback
 * 4. 构建工具执行上下文（包含历史对话）
 * 5. 依次执行每个工具调用，捕获执行结果
 * 6. 构建新的对话历史：[原始消息] + [LLM 工具调用消息] + [工具响应消息]
 * 7. 返回 ToolExecutionResult，包含更新后的对话历史和 returnDirect 标志
 * </pre>
 *
 * <p>
 * <b>与框架的关系：</b>
 * <ul>
 * <li>实现 Spring AI 的 ToolCallingManager 接口</li>
 * <li>与 ToolCallbackResolver 配合，解析和查找工具回调</li>
 * <li>与 ToolExecutionExceptionProcessor 配合，处理工具执行异常</li>
 * <li>支持 Micrometer Observation，提供可观测性</li>
 * </ul>
 *
 * @author gemo
 * @date 2026/1/15 15:15
 * @see org.springframework.ai.model.tool.ToolCallingManager
 * @see org.springframework.ai.tool.ToolCallback
 */
public class CustomToolCallingManager implements ToolCallingManager {
    private static final Logger logger = LoggerFactory.getLogger(CustomToolCallingManager.class);

    // @formatter:off

    private static final ObservationRegistry DEFAULT_OBSERVATION_REGISTRY
            = ObservationRegistry.NOOP;

    private static final ToolCallingObservationConvention DEFAULT_OBSERVATION_CONVENTION
            = new DefaultToolCallingObservationConvention();

    private static final ToolCallbackResolver DEFAULT_TOOL_CALLBACK_RESOLVER
            = new DelegatingToolCallbackResolver(List.of());

    private static final ToolExecutionExceptionProcessor DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR
            = DefaultToolExecutionExceptionProcessor.builder().build();

    private static final String POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING
            = "LLM may have adapted the tool name '{}', especially if the name was truncated due to length limits. If this is the case, you can customize the prefixing and processing logic using McpToolNamePrefixGenerator";


    // @formatter:on

    private final ObservationRegistry observationRegistry;

    private final ToolCallbackResolver toolCallbackResolver;

    private final ToolExecutionExceptionProcessor toolExecutionExceptionProcessor;

    private ToolCallingObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    public CustomToolCallingManager(ObservationRegistry observationRegistry, ToolCallbackResolver toolCallbackResolver,
            ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
        Assert.notNull(observationRegistry, "observationRegistry cannot be null");
        Assert.notNull(toolCallbackResolver, "toolCallbackResolver cannot be null");
        Assert.notNull(toolExecutionExceptionProcessor, "toolCallExceptionConverter cannot be null");

        this.observationRegistry = observationRegistry;
        this.toolCallbackResolver = toolCallbackResolver;
        this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
    }

    /**
     * 解析工具定义
     *
     * <p>
     * 从 ChatOptions 中提取所有可用的工具定义，包括：
     * <ul>
     * <li>直接提供的 ToolCallback 对象</li>
     * <li>通过工具名称引用的工具（需通过 ToolCallbackResolver 解析）</li>
     * </ul>
     *
     * <p>
     * <b>使用场景：</b>
     * 在构建 LLM 请求前，需要将所有可用工具的定义发送给 LLM，以便 LLM 知道可以调用哪些工具。
     *
     * @param chatOptions 聊天选项，包含 ToolCallback 列表和工具名称列表
     * @return 所有工具的定义列表（ToolDefinition），用于发送给 LLM
     * @throws IllegalStateException 如果通过名称找不到对应的 ToolCallback
     */
    @Override
    public List<ToolDefinition> resolveToolDefinitions(ToolCallingChatOptions chatOptions) {
        Assert.notNull(chatOptions, "chatOptions cannot be null");

        // 收集所有工具回调：先添加直接提供的 ToolCallback 对象
        List<ToolCallback> toolCallbacks = new ArrayList<>(chatOptions.getToolCallbacks());

        // 遍历工具名称，通过 ToolCallbackResolver 解析对应的 ToolCallback
        for (String toolName : chatOptions.getToolNames()) {
            // 如果该工具已经作为 ToolCallback 存在，则跳过（避免重复）
            // 这种情况可能发生在：同一个工具既以 ToolCallback 形式提供，又以名称形式引用
            if (chatOptions.getToolCallbacks()
                    .stream()
                    .anyMatch(tool -> tool.getToolDefinition().name().equals(toolName))) {
                continue;
            }
            // 通过解析器查找工具
            ToolCallback toolCallback = this.toolCallbackResolver.resolve(toolName);
            if (toolCallback == null) {
                logger.warn(POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING, toolName);
                throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
            }
            toolCallbacks.add(toolCallback);
        }

        // 将 ToolCallback 转换为 ToolDefinition（这是发送给 LLM 的格式）
        return toolCallbacks.stream().map(ToolCallback::getToolDefinition).toList();
    }

    /**
     * 执行工具调用
     *
     * <p>
     * 这是工具调用的核心入口方法，负责：
     * <ol>
     * <li>从 ChatResponse 中提取 LLM 返回的工具调用请求</li>
     * <li>构建工具执行上下文（包含对话历史）</li>
     * <li>执行实际的工具调用并收集结果</li>
     * <li>构建更新后的对话历史，用于下一轮 LLM 请求</li>
     * </ol>
     *
     * <p>
     * <b>对话历史构建流程：</b>
     * 
     * <pre>
     * 原始对话历史（Prompt.getInstructions()）
     *   + LLM 的工具调用消息（AssistantMessage，包含 ToolCall）
     *   + 工具执行结果消息（ToolResponseMessage，包含 ToolResponse）
     *   = 更新后的对话历史
     * </pre>
     *
     * <p>
     * <b>示例场景：</b>
     * 
     * <pre>
     * 1. 用户问："北京今天天气怎么样？"
     * 2. LLM 返回：调用 WeatherTool(toolName="beijing")
     * 3. 本方法执行 WeatherTool，得到天气数据
     * 4. 构建新的对话历史并发回 LLM
     * 5. LLM 基于工具结果生成最终回答："北京今天晴天，温度 25°C"
     * </pre>
     *
     * @param prompt       原始提示词，包含用户输入和之前的对话历史
     * @param chatResponse LLM 的响应，可能包含工具调用请求
     * @return 工具执行结果，包含更新后的对话历史和 returnDirect 标志
     * @throws IllegalStateException 如果 ChatResponse 中没有工具调用请求
     */
    @Override
    public @NonNull ToolExecutionResult executeToolCalls(@NonNull Prompt prompt, @NonNull ChatResponse chatResponse) {
        Assert.notNull(prompt, "prompt cannot be null");
        Assert.notNull(chatResponse, "chatResponse cannot be null");

        // 从 ChatResponse 中查找包含工具调用的 Generation
        // 一个 ChatResponse 可能包含多个 Generation（如 n > 1 时），这里取第一个包含工具调用的
        Optional<Generation> toolCallGeneration = chatResponse.getResults()
                .stream()
                .filter(g -> !CollectionUtils.isEmpty(g.getOutput().getToolCalls()))
                .findFirst();

        if (toolCallGeneration.isEmpty()) {
            throw new IllegalStateException("No tool call requested by the chat model");
        }

        // 获取 LLM 的 AssistantMessage（包含工具调用请求）
        AssistantMessage assistantMessage = toolCallGeneration.get().getOutput();

        // 构建工具执行上下文，包含对话历史等信息
        ToolContext toolContext = buildToolContext(prompt, assistantMessage);

        // 执行工具调用，获取工具响应
        CustomToolCallingManager.InternalToolExecutionResult internalToolExecutionResult = executeToolCall(prompt,
                assistantMessage,
                toolContext);

        // 构建执行后的对话历史：原始历史 + LLM 工具调用消息 + 工具响应消息
        List<Message> conversationHistory = buildConversationHistoryAfterToolExecution(prompt.getInstructions(),
                assistantMessage, internalToolExecutionResult.toolResponseMessage());

        // 返回工具执行结果
        return ToolExecutionResult.builder()
                .conversationHistory(List.of(internalToolExecutionResult.toolResponseMessage()))
                .returnDirect(internalToolExecutionResult.returnDirect())
                .build();
    }

    /**
     * 构建工具执行上下文
     *
     * <p>
     * ToolContext 是传递给 ToolCallback 的上下文对象，包含：
     * <ul>
     * <li>用户自定义的上下文数据（从 ChatOptions 中获取）</li>
     * <li>工具调用历史（TOOL_CALL_HISTORY），包含执行工具调用前的完整对话历史</li>
     * </ul>
     *
     * <p>
     * <b>为什么需要工具调用历史：</b>
     * 某些工具可能需要了解之前的对话内容来正确执行。例如：
     * <ul>
     * <li>多轮对话中，后续工具调用可能依赖之前工具的结果</li>
     * <li>工具需要理解完整的对话上下文才能做出正确决策</li>
     * </ul>
     *
     * @param prompt           提示词，包含对话选项和用户自定义的 toolContext
     * @param assistantMessage LLM 返回的助手消息（包含工具调用）
     * @return 工具执行上下文对象
     */
    private static ToolContext buildToolContext(Prompt prompt, AssistantMessage assistantMessage) {
        Map<String, Object> toolContextMap = Map.of();

        // 如果 ChatOptions 中定义了自定义的工具上下文，则复制过来
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions
                && !CollectionUtils.isEmpty(toolCallingChatOptions.getToolContext())) {
            toolContextMap = new HashMap<>(toolCallingChatOptions.getToolContext());

            // 添加工具调用历史：包含执行工具调用前的完整对话历史
            // 这对需要了解上下文的工具很重要
            toolContextMap.put(ToolContext.TOOL_CALL_HISTORY,
                    buildConversationHistoryBeforeToolExecution(prompt, assistantMessage));
        }

        return new ToolContext(toolContextMap);
    }

    /**
     * 构建工具执行前的对话历史
     *
     * <p>
     * 这个历史包含：
     * <ol>
     * <li>原始 Prompt 中的所有消息（用户消息、系统提示等）</li>
     * <li>LLM 返回的 AssistantMessage（包含工具调用请求）</li>
     * </ol>
     *
     * <p>
     * <b>用途：</b>
     * 将此历史放入 ToolContext 中，使工具能够了解完整的对话上下文。
     *
     * @param prompt           原始提示词
     * @param assistantMessage LLM 返回的助手消息（包含工具调用）
     * @return 工具执行前的对话历史
     */
    private static List<Message> buildConversationHistoryBeforeToolExecution(Prompt prompt,
            AssistantMessage assistantMessage) {
        // 复制 Prompt 中的所有消息
        List<Message> messageHistory = new ArrayList<>(prompt.copy().getInstructions());

        // 添加 LLM 的助手消息（包含工具调用请求）
        messageHistory.add(AssistantMessage.builder()
                .content(assistantMessage.getText())
                .properties(assistantMessage.getMetadata())
                .toolCalls(assistantMessage.getToolCalls())
                .build());
        return messageHistory;
    }

    /**
     * 执行工具调用并返回响应消息
     *
     * <p>
     * 这是实际执行工具的核心方法，负责：
     * <ol>
     * <li>从 Prompt 中获取可用的 ToolCallback 列表</li>
     * <li>遍历 AssistantMessage 中的所有工具调用请求</li>
     * <li>对每个工具调用，查找对应的 ToolCallback 并执行</li>
     * <li>在工具执行前后打印日志并通过 SSE 向前端发送状态消息</li>
     * <li>收集所有工具的执行结果</li>
     * <li>确定是否应该直接返回（returnDirect）</li>
     * </ol>
     *
     * <p>
     * <b>工具查找策略：</b>
     * 
     * <pre>
     * 1. 首先在 ChatOptions 提供的 ToolCallback 列表中查找
     * 2. 如果找不到，使用 ToolCallbackResolver 全局查找
     * 3. 如果都找不到，抛出 IllegalStateException
     * </pre>
     *
     * <p>
     * <b>异常处理：</b>
     * 工具执行抛出的 ToolExecutionException 会被 ToolExecutionExceptionProcessor 处理，
     * 将异常转换为友好的错误消息返回给 LLM。
     *
     * <p>
     * <b>可观测性与通知：</b>
     * <ul>
     * <li>每个工具调用都会通过 Micrometer Observation 进行记录，支持监控和追踪</li>
     * <li>工具执行前后会通过 Context 中的 SseEmitter 向前端发送 INVOCATION_CHANGE 消息</li>
     * </ul>
     *
     * @param prompt           提示词，包含可用的 ToolCallback 列表
     * @param assistantMessage LLM 返回的助手消息，包含工具调用请求
     * @param toolContext      工具执行上下文，包含对话历史等信息
     * @return 内部工具执行结果，包含工具响应消息和 returnDirect 标志
     * @throws IllegalStateException 如果找不到对应的 ToolCallback
     */
    private CustomToolCallingManager.InternalToolExecutionResult executeToolCall(Prompt prompt,
            AssistantMessage assistantMessage,
            ToolContext toolContext) {
        List<ToolCallback> toolCallbacks = List.of();
        if (prompt.getOptions() instanceof ToolCallingChatOptions toolCallingChatOptions) {
            toolCallbacks = toolCallingChatOptions.getToolCallbacks();
        }

        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();

        Boolean returnDirect = null;

        for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {

            logger.debug("Executing tool call: {}", toolCall.name());

            String toolName = toolCall.name();
            String toolInputArguments = toolCall.arguments();

            // Handle the possible null parameter situation in streaming mode.
            final String finalToolInputArguments;
            if (!StringUtils.hasText(toolInputArguments)) {
                logger.warn("Tool call arguments are null or empty for tool: {}. Using empty JSON object as default.",
                        toolName);
                finalToolInputArguments = "{}";
            } else {
                finalToolInputArguments = toolInputArguments;
            }

            ToolCallback toolCallback = toolCallbacks.stream()
                    .filter(tool -> toolName.equals(tool.getToolDefinition().name()))
                    .findFirst()
                    .orElseGet(() -> this.toolCallbackResolver.resolve(toolName));

            if (toolCallback == null) {
                logger.warn(POSSIBLE_LLM_TOOL_NAME_CHANGE_WARNING, toolName);
                throw new IllegalStateException("No ToolCallback found for tool name: " + toolName);
            }

            if (returnDirect == null) {
                returnDirect = toolCallback.getToolMetadata().returnDirect();
            } else {
                returnDirect = returnDirect && toolCallback.getToolMetadata().returnDirect();
            }

            if (toolCallback.getToolMetadata() instanceof CustomToolMetadata customToolMetadata) {
                HashMap<String, Object> map = new HashMap<>(toolContext.getContext());
                map.put(ToolContextKeys.CUSTOM_TOOL_METADATA, customToolMetadata);
                toolContext = new ToolContext(map);
            }

            ToolCallingObservationContext observationContext = ToolCallingObservationContext.builder()
                    .toolDefinition(toolCallback.getToolDefinition())
                    .toolMetadata(toolCallback.getToolMetadata())
                    .toolCallArguments(finalToolInputArguments)
                    .build();

            // 打印工具执行前的日志并发送 SSE 消息
            SuperAgentContext sessionContext = (SuperAgentContext) toolContext.getContext()
                    .get(ToolContextKeys.SESSION_CONTEXT);
            // 生成唯一的 invocation_id
            String invocationId = "invocationId_" + IdUtil.fastSimpleUUID();

            logToolExecutionStart(toolCallback, sessionContext, invocationId);

            ToolContext finalToolContext = toolContext;
            String toolCallResult = ToolCallingObservationDocumentation.TOOL_CALL
                    .observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
                            this.observationRegistry)
                    .observe(() -> {
                        String toolResult;
                        try {
                            toolResult = toolCallback.call(finalToolInputArguments, finalToolContext);
                        } catch (ToolExecutionException ex) {
                            toolResult = this.toolExecutionExceptionProcessor.process(ex);
                        }
                        observationContext.setToolCallResult(toolResult);
                        return toolResult;
                    });

            // 打印工具执行后的日志并发送 SSE 消息
            logToolExecutionComplete(toolCallback, sessionContext, invocationId);

            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolName,
                    toolCallResult != null ? toolCallResult : ""));
        }

        return new CustomToolCallingManager.InternalToolExecutionResult(
                ToolResponseMessage.builder().responses(toolResponses).build(),
                returnDirect);
    }

    /**
     * 构建工具执行后的完整对话历史
     *
     * <p>
     * 这是最终的对话历史，将用于下一轮 LLM 请求。包含：
     * <ol>
     * <li>执行工具前的所有消息（previousMessages）</li>
     * <li>LLM 的工具调用消息（assistantMessage）</li>
     * <li>工具执行结果消息（toolResponseMessage）</li>
     * </ol>
     *
     * <p>
     * <b>对话历史流转：</b>
     * 
     * <pre>
     * 第1轮：[用户消息] → LLM → [LLM工具调用] + [工具响应] → 对话历史1
     * 第2轮：对话历史1 → LLM → [最终回复] → 对话历史2
     * </pre>
     *
     * @param previousMessages    工具执行前的对话历史
     * @param assistantMessage    LLM 的工具调用消息
     * @param toolResponseMessage 工具执行结果消息
     * @return 更新后的完整对话历史
     */
    private List<Message> buildConversationHistoryAfterToolExecution(List<Message> previousMessages,
            AssistantMessage assistantMessage, ToolResponseMessage toolResponseMessage) {
        List<Message> messages = new ArrayList<>(previousMessages);
        messages.add(assistantMessage);
        messages.add(toolResponseMessage);
        return messages;
    }

    /**
     * 设置工具调用观测约定
     *
     * <p>
     * 用于自定义 Micrometer Observation 的命名和标签生成策略。
     *
     * @param observationConvention 自定义的观测约定
     */
    public void setObservationConvention(ToolCallingObservationConvention observationConvention) {
        this.observationConvention = observationConvention;
    }

    /**
     * 创建 Builder 构建器
     *
     * @return CustomToolCallingManager.Builder 实例
     */
    public static CustomToolCallingManager.Builder builder() {
        return new CustomToolCallingManager.Builder();
    }

    /**
     * 内部工具执行结果
     *
     * <p>
     * 这是一个记录类（record），用于封装工具执行的中间结果：
     * <ul>
     * <li>toolResponseMessage - 工具响应消息，包含所有工具的执行结果</li>
     * <li>returnDirect - 是否直接返回标志，如果为 true 则应立即将工具结果返回给用户，而不继续下一轮 LLM 调用</li>
     * </ul>
     *
     * <p>
     * <b>returnDirect 的语义：</b>
     * 当某个工具标记为 returnDirect=true 时，表示该工具的执行结果应该直接返回给用户，
     * 而不是继续让 LLM 处理。这在某些场景下很有用，例如：
     * <ul>
     * <li>工具已经生成了最终答案，不需要 LLM 再次处理</li>
     * <li>工具执行结果本身就是用户想要的格式（如 JSON、文件等）</li>
     * </ul>
     *
     * @param toolResponseMessage 工具响应消息
     * @param returnDirect        是否直接返回
     */
    private record InternalToolExecutionResult(ToolResponseMessage toolResponseMessage, boolean returnDirect) {
    }

    /**
     * 打印工具执行开始的日志并发送 SSE 消息
     *
     * <p>
     * 根据工具类型（MCP 或 SubAgent）打印不同格式的日志
     *
     * @param toolCallback 工具回调对象
     * @param context      会话上下文，用于发送 SSE 消息
     */
    private void logToolExecutionStart(ToolCallback toolCallback, SuperAgentContext context, String invocationId) {

        ToolDefinition toolDefinition = toolCallback.getToolDefinition();
        String toolName = toolDefinition.name();
        String toolDescription = toolDefinition.description();

        ToolMetadata toolMetadata = toolCallback.getToolMetadata();

        // 判断是否为 CustomToolMetadata 实例
        if (toolMetadata instanceof CustomToolMetadata customMetadata) {
            String type = customMetadata.getType();
            String name = customMetadata.getName();
            String description = customMetadata.getDescription();

            // 根据类型打印不同格式的日志
            if (CustomToolMetadata.ToolMetadataAttribute.TYPE_MCP.equals(type)) {
                logger.info("正在执行 MCP - {} - {}", toolName, toolDescription);
                // 发送 SSE 消息给前端
                ToolExecutionMessageHelper.sendMcpExecutionStart(toolCallback, context, invocationId);
            } else if (CustomToolMetadata.ToolMetadataAttribute.TYPE_SUB_AGENT.equals(type)) {
                logger.info("正在执行 SubAgent - {} - {}", name, description);
            } else {
                // 未知类型，使用通用格式
                logger.info("正在执行工具 - {} - {}", name, description);
            }
        } else {
            // 非 CustomToolMetadata 实例，使用工具定义中的信息
            logger.info("正在执行工具 - {} - {}", toolName, toolDescription);
        }
    }

    /**
     * 打印工具执行完成的日志并发送 SSE 消息
     *
     * <p>
     * 根据工具类型（MCP 或 SubAgent）打印不同格式的日志
     *
     * @param toolCallback 工具回调对象
     * @param context      会话上下文，用于发送 SSE 消息
     */
    private void logToolExecutionComplete(ToolCallback toolCallback, SuperAgentContext context, String invocationId) {

        ToolDefinition toolDefinition = toolCallback.getToolDefinition();

        ToolMetadata toolMetadata = toolCallback.getToolMetadata();

        // 判断是否为 CustomToolMetadata 实例
        if (toolMetadata instanceof CustomToolMetadata customMetadata) {
            String type = customMetadata.getType();
            String name = customMetadata.getName();
            String description = customMetadata.getDescription();

            // 根据类型打印不同格式的日志
            if (CustomToolMetadata.ToolMetadataAttribute.TYPE_MCP.equals(type)) {
                logger.info("MCP执行完成 - {} - {}", name, description);
                // 发送 SSE 消息给前端
                ToolExecutionMessageHelper.sendMcpExecutionComplete(toolCallback, context, invocationId);
            } else if (CustomToolMetadata.ToolMetadataAttribute.TYPE_SUB_AGENT.equals(type)) {
                logger.info("SubAgent执行完成 - {} - {}", name, description);
            } else {
                // 未知类型，使用通用格式
                logger.info("工具执行完成 - {} - {}", name, description);
            }
        } else {
            // 非 CustomToolMetadata 实例，使用工具定义中的信息
            logger.info("工具执行完成 - {} - {}", toolDefinition.name(), toolDefinition.description());
        }

    }

    /**
     * CustomToolCallingManager 的构建器
     *
     * <p>
     * 提供流式 API 来构建 CustomToolCallingManager 实例。
     *
     * <p>
     * <b>使用示例：</b>
     * 
     * <pre>{@code
     * CustomToolCallingManager manager = CustomToolCallingManager.builder()
     *         .observationRegistry(observationRegistry)
     *         .toolCallbackResolver(toolCallbackResolver)
     *         .toolExecutionExceptionProcessor(exceptionProcessor)
     *         .build();
     * }</pre>
     */
    public final static class Builder {

        /**
         * 观测注册表，用于记录工具调用的可观测性数据
         */
        private ObservationRegistry observationRegistry = DEFAULT_OBSERVATION_REGISTRY;

        /**
         * 工具回调解析器，用于根据工具名称查找对应的 ToolCallback
         */
        private ToolCallbackResolver toolCallbackResolver = DEFAULT_TOOL_CALLBACK_RESOLVER;

        /**
         * 工具执行异常处理器，用于处理工具执行过程中的异常
         */
        private ToolExecutionExceptionProcessor toolExecutionExceptionProcessor = DEFAULT_TOOL_EXECUTION_EXCEPTION_PROCESSOR;

        /**
         * 私有构造函数
         */
        private Builder() {
        }

        /**
         * 设置观测注册表
         *
         * @param observationRegistry Micrometer Observation 注册表
         * @return this
         */
        public CustomToolCallingManager.Builder observationRegistry(ObservationRegistry observationRegistry) {
            this.observationRegistry = observationRegistry;
            return this;
        }

        /**
         * 设置工具回调解析器
         *
         * @param toolCallbackResolver 工具回调解析器
         * @return this
         */
        public CustomToolCallingManager.Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
            this.toolCallbackResolver = toolCallbackResolver;
            return this;
        }

        /**
         * 设置工具执行异常处理器
         *
         * @param toolExecutionExceptionProcessor 工具执行异常处理器
         * @return this
         */
        public CustomToolCallingManager.Builder toolExecutionExceptionProcessor(
                ToolExecutionExceptionProcessor toolExecutionExceptionProcessor) {
            this.toolExecutionExceptionProcessor = toolExecutionExceptionProcessor;
            return this;
        }

        /**
         * 构建 CustomToolCallingManager 实例
         *
         * @return 新的 CustomToolCallingManager 实例
         */
        public CustomToolCallingManager build() {
            return new CustomToolCallingManager(this.observationRegistry, this.toolCallbackResolver,
                    this.toolExecutionExceptionProcessor);
        }

    }
}
