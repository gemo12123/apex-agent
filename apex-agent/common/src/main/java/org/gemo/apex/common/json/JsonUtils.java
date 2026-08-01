package org.gemo.apex.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.gemo.apex.common.exception.JsonDecodingException;
import org.gemo.apex.common.exception.JsonEncodingException;

import java.lang.reflect.Modifier;

public final class JsonUtils {
    private static final ObjectMapper MAPPER = createMapper();

    private JsonUtils() {
    }

    private static ObjectMapper createMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static String toJson(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new JsonEncodingException(value.getClass(), exception);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) return null;
        if (type == null) throw new IllegalArgumentException("type 不能为空");
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new JsonDecodingException(type.getTypeName(), exception);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new JsonDecodingException(type.getType().getTypeName(), exception);
        }
    }

    public static JsonNode toTree(Object value) {
        if (value == null) return null;
        try {
            return MAPPER.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            throw new JsonEncodingException(value.getClass(), exception);
        }
    }

    public static JsonNode parseTree(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new JsonDecodingException(JsonNode.class.getName(), exception);
        }
    }

    public static <T> T convert(Object source, Class<T> type) {
        if (source == null) return null;
        requireConcrete(type);
        try {
            return MAPPER.convertValue(source, type);
        } catch (IllegalArgumentException exception) {
            throw new JsonDecodingException(type.getTypeName(), exception);
        }
    }

    public static <T> T deepCopy(Object source, Class<T> type) {
        if (source == null) return null;
        requireConcrete(type);
        return fromJson(toJson(source), type);
    }

    public static <T> T deepCopy(Object source, TypeReference<T> type) {
        if (source == null) return null;
        return fromJson(toJson(source), type);
    }

    public static ObjectMapper mapperCopy() {
        return MAPPER.copy();
    }

    private static void requireConcrete(Class<?> type) {
        if (type == null || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("目标类型必须是具体类型");
        }
    }
}
