package org.gemo.apex.tool.metadata;

import lombok.Getter;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ToolMetadata 接口的默认实现类
 * 用于存储工具的元数据信息，包括 name、description 等属性
 *
 * 支持的工具类型：
 * - MCP (Model Context Protocol) 工具
 * - SubAgent 工具
 *
 * @author Super-Zhang
 * @date 2026/1/15
 */
public class CustomToolMetadata implements ToolMetadata {

    /**
     * 工具结果是否直接返回给调用者
     */
    private final boolean returnDirect;

    /**
     * 扩展属性映射，用于存储工具的各种元数据信息
     * 常用属性包括：
     * - name: 工具名称
     * - description: 工具描述
     * - type: 工具类型 (MCP/SubAgent)
     * - url: SubAgent 的调用 URL
     * - timeout: 超时设置（毫秒）
     * -- GETTER --
     *  获取所有属性
     *
     * @return 不可变的属性映射

     */
    @Getter
    private final Map<String, Object> attributes;

    /**
     * 私有构造函数，使用 Builder 模式创建实例
     */
    private CustomToolMetadata(Builder builder) {
        this.returnDirect = builder.returnDirect;
        this.attributes = Collections.unmodifiableMap(new HashMap<>(builder.attributes));
    }

    @Override
    public boolean returnDirect() {
        return this.returnDirect;
    }

    /**
     * 获取指定属性的值
     *
     * @param key 属性键
     * @return 属性值，如果不存在则返回 null
     */
    @Nullable
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * 获取指定属性的值，并提供默认值
     *
     * @param key          属性键
     * @param defaultValue 默认值
     * @return 属性值，如果不存在则返回默认值
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, @Nullable T defaultValue) {
        Object value = this.attributes.get(key);
        if (value == null) {
            return defaultValue;
        }
        return (T) value;
    }

    /**
     * 获取工具名称
     *
     * @return 工具名称，如果未设置则返回 null
     */
    @Nullable
    public String getName() {
        return getAttribute(ToolMetadataAttribute.NAME, null);
    }

    /**
     * 获取工具描述
     *
     * @return 工具描述，如果未设置则返回 null
     */
    @Nullable
    public String getDescription() {
        return getAttribute(ToolMetadataAttribute.DESCRIPTION, null);
    }

    /**
     * 获取工具类型
     *
     * @return 工具类型，如果未设置则返回 null
     */
    @Nullable
    public String getType() {
        return getAttribute(ToolMetadataAttribute.TYPE, null);
    }

    /**
     * 获取 SubAgent 的调用 URL
     *
     * @return URL，如果未设置则返回 null
     */
    @Nullable
    public String getUrl() {
        return getAttribute(ToolMetadataAttribute.URL, null);
    }

    /**
     * 获取超时设置
     *
     * @return 超时时间（毫秒），如果未设置则返回 null
     */
    @Nullable
    public Integer getTimeout() {
        return getAttribute(ToolMetadataAttribute.TIMEOUT, null);
    }

    /**
     * 获取显示名称
     * 用于存储配置文件中的原始 name 字段值
     *
     * @return 显示名称，如果未设置则返回 null
     */
    @Nullable
    public String getDisplayName() {
        return getAttribute(ToolMetadataAttribute.DISPLAY_NAME, getAttribute(ToolMetadataAttribute.NAME, ""));
    }

    /**
     * 创建新的 Builder 实例
     *
     * @return Builder 实例
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从 Method 创建 ToolMetadata 实例
     * 这个方法与 Spring AI 框架的 ToolMetadata.from(Method) 方法兼容
     *
     * @param method 方法对象
     * @return ToolMetadata 实例
     */
    public static ToolMetadata from(Method method) {
        Assert.notNull(method, "method cannot be null");
        // 这里需要调用 ToolUtils.getToolReturnDirect(method)
        // 但为了保持简单，暂时使用默认值
        // 实际使用时可以考虑引入 Spring AI 的 ToolUtils
        return builder()
                .returnDirect(false) // 默认不直接返回
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomToolMetadata that = (CustomToolMetadata) o;
        return returnDirect == that.returnDirect && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnDirect, attributes);
    }

    @Override
    public String toString() {
        return "DefaultToolMetadata{" +
                "returnDirect=" + returnDirect +
                ", attributes=" + attributes +
                '}';
    }

    /**
     * Builder 类，用于构建 DefaultToolMetadata 实例
     */
    public static class Builder {
        private boolean returnDirect = false;
        private final Map<String, Object> attributes = new HashMap<>();

        private Builder() {
        }

        /**
         * 设置工具结果是否直接返回给调用者
         *
         * @param returnDirect 是否直接返回
         * @return Builder 实例
         */
        public Builder returnDirect(boolean returnDirect) {
            this.returnDirect = returnDirect;
            return this;
        }

        /**
         * 设置工具名称
         *
         * @param name 工具名称
         * @return Builder 实例
         */
        public Builder name(@Nullable String name) {
            return attribute(ToolMetadataAttribute.NAME, name);
        }

        /**
         * 设置工具描述
         *
         * @param description 工具描述
         * @return Builder 实例
         */
        public Builder description(@Nullable String description) {
            return attribute(ToolMetadataAttribute.DESCRIPTION, description);
        }

        /**
         * 设置工具类型
         *
         * @param type 工具类型 (如 ToolMetadataAttribute.TYPE_MCP, ToolMetadataAttribute.TYPE_SUB_AGENT)
         * @return Builder 实例
         */
        public Builder type(@Nullable String type) {
            return attribute(ToolMetadataAttribute.TYPE, type);
        }

        /**
         * 设置 SubAgent 的调用 URL
         *
         * @param url URL 地址
         * @return Builder 实例
         */
        public Builder url(@Nullable String url) {
            return attribute(ToolMetadataAttribute.URL, url);
        }

        /**
         * 设置超时时间
         *
         * @param timeout 超时时间（毫秒）
         * @return Builder 实例
         */
        public Builder timeout(@Nullable Integer timeout) {
            return attribute(ToolMetadataAttribute.TIMEOUT, timeout);
        }

        /**
         * 设置显示名称
         * 用于存储配置文件中的原始 name 字段值
         *
         * @param displayName 显示名称
         * @return Builder 实例
         */
        public Builder displayName(@Nullable String displayName) {
            return attribute(ToolMetadataAttribute.DISPLAY_NAME, displayName);
        }

        /**
         * 设置单个属性
         *
         * @param key   属性键
         * @param value 属性值
         * @return Builder 实例
         */
        public Builder attribute(String key, @Nullable Object value) {
            if (value != null) {
                this.attributes.put(key, value);
            }
            return this;
        }

        /**
         * 批量设置属性
         *
         * @param attributes 属性映射
         * @return Builder 实例
         */
        public Builder attributes(@Nullable Map<String, Object> attributes) {
            if (attributes != null) {
                this.attributes.putAll(attributes);
            }
            return this;
        }

        /**
         * 构建 DefaultToolMetadata 实例
         *
         * @return DefaultToolMetadata 实例
         */
        public CustomToolMetadata build() {
            return new CustomToolMetadata(this);
        }
    }

    /**
     * 工具元数据属性常量定义
     */
    public static final class ToolMetadataAttribute {
        /**
         * 工具名称
         */
        public static final String NAME = "name";

        /**
         * 工具描述
         */
        public static final String DESCRIPTION = "description";

        /**
         * 工具类型
         */
        public static final String TYPE = "type";

        /**
         * 工具类型: MCP (Model Context Protocol)
         */
        public static final String TYPE_MCP = "MCP";

        /**
         * 工具类型: SubAgent
         */
        public static final String TYPE_SUB_AGENT = "SubAgent";

        /**
         * SubAgent 的调用 URL
         */
        public static final String URL = "url";

        /**
         * 超时时间（毫秒）
         */
        public static final String TIMEOUT = "timeout";

        /**
         * 显示名称
         * 用于存储配置文件中的原始 name 字段值
         */
        public static final String DISPLAY_NAME = "displayName";

        private ToolMetadataAttribute() {
            // 私有构造函数，防止实例化
        }
    }
}
