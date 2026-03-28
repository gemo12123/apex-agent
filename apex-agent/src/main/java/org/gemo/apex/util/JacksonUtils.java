package org.gemo.apex.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.TimeZone;

/**
 * @author gemo
 * @date 2026/1/16 15:03
 */
public class JacksonUtils {

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            // 1. Java 8 时间支持（LocalDateTime / LocalDate / Instant 等）
            .addModule(new JavaTimeModule())

            // 2. 忽略未知字段（防止前端/下游多传字段直接炸）
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

            // 3. 空对象不序列化
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)

            // 4. 日期不转时间戳
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

            // 5. 只序列化非空字段（null 不输出）
            .serializationInclusion(JsonInclude.Include.NON_NULL)

            // 6. 统一时区（非常重要）
            .defaultTimeZone(TimeZone.getTimeZone("Asia/Shanghai"))

            .build();

    static {
        com.fasterxml.jackson.databind.module.SimpleModule springAiModule = new com.fasterxml.jackson.databind.module.SimpleModule();
        springAiModule.addDeserializer(org.springframework.ai.chat.messages.Message.class, new MessageDeserializer());
        OBJECT_MAPPER.registerModule(springAiModule);
    }

    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }

    /* ===================== 序列化 ===================== */

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /* ===================== 反序列化 ===================== */

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeRef);
        } catch (IOException e) {
            throw new RuntimeException("JSON 反序列化失败", e);
        }
    }

    /* ===================== 进阶用法 ===================== */

    public static JsonNode toTree(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("JSON 转 JsonNode 失败", e);
        }
    }

    public static <T> T convert(Object source, Class<T> targetType) {
        return OBJECT_MAPPER.convertValue(source, targetType);
    }
}
