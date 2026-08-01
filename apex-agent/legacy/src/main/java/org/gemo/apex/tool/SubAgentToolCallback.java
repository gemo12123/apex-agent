package org.gemo.apex.tool;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.gemo.apex.constant.ToolContextKeys;
import org.gemo.apex.context.SuperAgentContext;
import org.gemo.apex.memory.context.UserContextFilter;
import org.gemo.apex.tool.handler.MessageHandlerContext;
import org.gemo.apex.tool.handler.MessageProcessor;
import org.gemo.apex.tool.metadata.CustomToolMetadata;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 子智能体工具回调
 * 通过 HTTP 请求调用 SubAgent，解析 SSE 流式响应中的 AgentMessage
 *
 * @author gemo
 */
@Slf4j
@Getter
public class SubAgentToolCallback implements ToolCallback {

    private final String url;
    private final ToolDefinition toolDefinition;
    private final CustomToolMetadata toolMetadata;
    private final CloseableHttpClient httpClient;
    private final Integer timeout;
    private final MessageProcessor messageProcessor;

    public SubAgentToolCallback(String url, ToolDefinition toolDefinition, CustomToolMetadata toolMetadata,
            Integer timeout, MessageProcessor messageProcessor) {
        this.url = url;
        this.toolDefinition = toolDefinition;
        this.toolMetadata = toolMetadata;
        this.timeout = timeout != null ? timeout : 60000;
        this.messageProcessor = messageProcessor;
        this.httpClient = HttpClients.createDefault();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return this.toolDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return this.toolMetadata;
    }

    @Override
    public String call(String toolInput, @Nullable ToolContext toolContext) {
        JSONObject input = JSON.parseObject(toolInput);

        // 1. 设置 sessionId
        String sessionId = input.getString("sessionId");
        if (!StringUtils.hasText(sessionId)) {
            // 如果 LLM 没有传入，则默认生成一个隔离的嵌套 sessionId
            sessionId = UUID.randomUUID().toString();
            input.put("sessionId", sessionId);
        }

        // 2. 设置 agentKey（取自配置文件 map 的 key，即 tool 的 name）
        input.put("agentKey", toolDefinition.name());

        log.info("SubAgentToolCallback 调用 SubAgent: {}, agentKey: {}, 输入: {}", url, toolDefinition.name(), toolInput);


        try {
            // 配置请求超时
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(30000)
                    .setSocketTimeout(timeout)
                    .build();

            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            extractSuperAgentContext(toolContext)
                    .map(SuperAgentContext::getUserId)
                    .filter(StringUtils::hasText)
                    .ifPresent(userId -> httpPost.setHeader(UserContextFilter.USER_ID_HEADER, userId));
            httpPost.setConfig(requestConfig);
            httpPost.setEntity(new StringEntity(input.toJSONString(), ContentType.APPLICATION_JSON));

            HttpResponse response = httpClient.execute(httpPost);

            MessageHandlerContext messageHandlerContext;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()))) {
                messageHandlerContext = parseStreamResponse(reader, toolContext);
            }

            log.info("SubAgent 调用完成");
            return messageHandlerContext.getResultBuilder().toString();
        } catch (Exception e) {
            log.error("SubAgent 调用失败，URL：{}，失败原因： {}", url, e.getMessage(), e);
            return "调用失败！！！";
        }
    }

    @Override
    public String call(@NotNull String toolInput) {
        return call(toolInput, null);
    }

    /**
     * 解析流式响应
     * 支持两种格式:
     * 1. SSE 格式: data: {...}
     * 2. 普通流式行格式
     *
     * @param reader      响应读取器
     * @param toolContext 工具上下文
     * @return 处理上下文（包含拼接后的完整响应）
     */
    private MessageHandlerContext parseStreamResponse(BufferedReader reader, ToolContext toolContext) {
        // 构建处理上下文
        MessageHandlerContext context = MessageHandlerContext.builder()
                .resultBuilder(new StringBuilder())
                .superAgentContext(extractSuperAgentContext(toolContext))
                .toolContext(extractToolContext(toolContext))
                .build();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }

                // 移除 SSE 前缀
                if (line.startsWith("data:")) {
                    line = line.substring(5).trim();
                }

                // 处理消息
                messageProcessor.process(line, context);

                // 检查是否完成
                if (context.isComplete()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.error("读取流式响应失败: {}", e.getMessage(), e);
            StringBuilder resultBuilder = context.getResultBuilder();
            if (!resultBuilder.isEmpty()) {
                resultBuilder.append("\n\n");
            }
            resultBuilder.append("工具调用失败！");
        }

        return context;
    }

    /**
     * 从 ToolContext 中提取 SuperAgentContext
     */
    private Optional<SuperAgentContext> extractSuperAgentContext(ToolContext toolContext) {
        return Optional.ofNullable(toolContext)
                .map(ToolContext::getContext)
                .map(ctx -> ctx.get(ToolContextKeys.SESSION_CONTEXT))
                .filter(obj -> obj instanceof SuperAgentContext)
                .map(obj -> (SuperAgentContext) obj);
    }

    /**
     * 从 ToolContext 中提取 Tool 上下文
     */
    private Optional<Map<String, Object>> extractToolContext(ToolContext toolContext) {
        return Optional.ofNullable(toolContext)
                .map(ToolContext::getContext);
    }

    /**
     * Builder 模式
     */
    public static class Builder {
        private String name;
        private String displayName;
        private String description;
        private String inputSchema;
        private String url;
        private ToolDefinition toolDefinition;
        private CustomToolMetadata toolMetadata;
        private Integer timeout;
        private MessageProcessor messageProcessor;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder toolDefinition(ToolDefinition toolDefinition) {
            this.toolDefinition = toolDefinition;
            return this;
        }

        public Builder toolMetadata(CustomToolMetadata toolMetadata) {
            this.toolMetadata = toolMetadata;
            return this;
        }

        public Builder timeout(Integer timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder messageProcessor(MessageProcessor messageProcessor) {
            this.messageProcessor = messageProcessor;
            return this;
        }

        public SubAgentToolCallback build() {
            // 构建 ToolDefinition
            this.toolDefinition = this.toolDefinition != null ? this.toolDefinition
                    : DefaultToolDefinition.builder()
                            .name(this.name)
                            .description(StringUtils.hasText(this.description) ? this.description
                                    : ToolUtils.getToolDescriptionFromName(this.name))
                            .inputSchema(StringUtils.hasText(this.inputSchema) ? this.inputSchema
                                    : JsonSchemaGenerator.generateForType(Input.class))
                            .build();

            // 构建 ToolMetadata（如果未提供）
            if (this.toolMetadata == null) {
                this.toolMetadata = CustomToolMetadata.builder()
                        .name(this.name)
                        .displayName(this.displayName)
                        .description(this.description)
                        .type(CustomToolMetadata.ToolMetadataAttribute.TYPE_SUB_AGENT)
                        .url(this.url)
                        .timeout(this.timeout)
                        .build();
            }

            return new SubAgentToolCallback(this.url, this.toolDefinition, this.toolMetadata, this.timeout,
                    this.messageProcessor);
        }
    }

    @Data
    private static class Input {
        @JsonPropertyDescription("问题输入。对输入的描述必须全面而详细，必须包含需要解决的问题描述、任何额外的背景信息和疑问、用户的原始目标。")
        private String query;
    }
}
