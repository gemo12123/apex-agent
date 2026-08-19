package org.gemo.apex.kit.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.extension.hook.LifecycleHook;

/** 对超长 JSON 工具结果做结构化截断：完整原文落盘，返回 preview 与 truncation_info 信封。 */
public final class JsonTruncateHook
        implements LifecycleHook<PostToolCallContext, PostToolCallHookResult> {
    public static final String REGISTRATION_NAME = "jsonTruncateHook";
    public static final String OPTION_MAX_SIZE = "maxSize";
    public static final String OPTION_MAX_ARRAY_ELEMENTS = "maxArrayElements";
    public static final String OPTION_MAX_OBJECT_FIELDS = "maxObjectFields";
    public static final String OPTION_MAX_STRING_LENGTH = "maxStringLength";
    public static final String OPTION_OUTPUT_DIR = "outputDir";
    public static final int DEFAULT_MAX_SIZE = 8000;
    public static final int DEFAULT_MAX_ARRAY_ELEMENTS = 3;
    public static final int DEFAULT_MAX_OBJECT_FIELDS = 10;
    public static final int DEFAULT_MAX_STRING_LENGTH = 200;
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.POST_TOOL_CALL,
                    PostToolCallContext.class,
                    PostToolCallHookResult.class);

    private final int maxSize;
    private final int maxArrayElements;
    private final int maxObjectFields;
    private final int maxStringLength;
    private final Path outputDir;

    public JsonTruncateHook() {
        this(
                DEFAULT_MAX_SIZE,
                DEFAULT_MAX_ARRAY_ELEMENTS,
                DEFAULT_MAX_OBJECT_FIELDS,
                DEFAULT_MAX_STRING_LENGTH,
                Path.of(System.getProperty("java.io.tmpdir"), "apex-json-truncate"));
    }

    public JsonTruncateHook(
            int maxSize,
            int maxArrayElements,
            int maxObjectFields,
            int maxStringLength,
            Path outputDir) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize 必须大于 0");
        }
        if (maxArrayElements < 1) {
            throw new IllegalArgumentException("maxArrayElements 必须 >= 1");
        }
        if (maxObjectFields < 1) {
            throw new IllegalArgumentException("maxObjectFields 必须 >= 1");
        }
        if (maxStringLength < 1) {
            throw new IllegalArgumentException("maxStringLength 必须 >= 1");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir 不能为空");
        }
        this.maxSize = maxSize;
        this.maxArrayElements = maxArrayElements;
        this.maxObjectFields = maxObjectFields;
        this.maxStringLength = maxStringLength;
        this.outputDir = outputDir;
    }

    @Override
    public String name() {
        return REGISTRATION_NAME;
    }

    @Override
    public HookTypeDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public PostToolCallHookResult apply(PostToolCallContext context) {
        String content = context.toolResult().content();
        JsonNode root = tryParse(content);
        Map<String, Object> options = context.binding().options();
        if (root == null
                || content.codePointCount(0, content.length())
                        <= resolveInt(options, OPTION_MAX_SIZE, maxSize)) {
            return keep(context);
        }

        TruncationResult truncated =
                truncate(
                        root,
                        resolveInt(options, OPTION_MAX_ARRAY_ELEMENTS, maxArrayElements),
                        resolveInt(options, OPTION_MAX_OBJECT_FIELDS, maxObjectFields),
                        resolveInt(options, OPTION_MAX_STRING_LENGTH, maxStringLength));

        Path file;
        try {
            file = writeFullResult(content, context, resolveOutputDir(options));
        } catch (IOException exception) {
            return keep(context);
        }

        Map<String, Object> fileRef = new LinkedHashMap<>();
        fileRef.put("file_path", file.toAbsolutePath().toString());
        fileRef.put("content_type", "application/json");

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("truncated", true);
        envelope.put("data_preview", truncated.preview());
        envelope.put("truncation_info", truncated.info());
        envelope.put("full_result_file", fileRef);
        return new ContinuePostToolCall(
                HookMutations.none(),
                new ToolResultPatch(
                        JsonUtils.toJson(envelope), context.toolResult().metadata()));
    }

    private record TruncationResult(JsonNode preview, Map<String, Object> info) {}

    private static TruncationResult truncate(
            JsonNode root, int maxArrayElements, int maxObjectFields, int maxStringLength) {
        Map<String, Object> info = new LinkedHashMap<>();
        JsonNode preview =
                truncateNode(
                        root, "$", maxArrayElements, maxObjectFields, maxStringLength, info);
        return new TruncationResult(preview, info);
    }

    private static JsonNode truncateNode(
            JsonNode node,
            String path,
            int maxArrayElements,
            int maxObjectFields,
            int maxStringLength,
            Map<String, Object> info) {
        if (node.isArray()) {
            return truncateArray(node, path, maxArrayElements, maxObjectFields, maxStringLength, info);
        }
        if (node.isObject()) {
            return truncateObject(node, path, maxArrayElements, maxObjectFields, maxStringLength, info);
        }
        if (node.isTextual()) {
            return truncateText(node, path, maxStringLength, info);
        }
        return node;
    }

    private static JsonNode truncateArray(
            JsonNode node,
            String path,
            int maxArrayElements,
            int maxObjectFields,
            int maxStringLength,
            Map<String, Object> info) {
        int total = node.size();
        int kept = Math.min(total, maxArrayElements);
        if (total > kept) {
            record(info, path, "array", Map.of("original_length", total, "kept", kept));
        }
        ArrayNode result = NODES.arrayNode(kept);
        for (int i = 0; i < kept; i++) {
            result.add(
                    truncateNode(
                            node.get(i),
                            path + "[" + i + "]",
                            maxArrayElements,
                            maxObjectFields,
                            maxStringLength,
                            info));
        }
        return result;
    }

    private static JsonNode truncateObject(
            JsonNode node,
            String path,
            int maxArrayElements,
            int maxObjectFields,
            int maxStringLength,
            Map<String, Object> info) {
        int total = node.size();
        int kept = Math.min(total, maxObjectFields);
        if (total > kept) {
            record(info, path, "object", Map.of("original_fields", total, "kept", kept));
        }
        ObjectNode result = NODES.objectNode();
        var fields = node.fields();
        int i = 0;
        while (fields.hasNext() && i < kept) {
            var field = fields.next();
            result.set(
                    field.getKey(),
                    truncateNode(
                            field.getValue(),
                            path + "." + field.getKey(),
                            maxArrayElements,
                            maxObjectFields,
                            maxStringLength,
                            info));
            i++;
        }
        return result;
    }

    private static JsonNode truncateText(
            JsonNode node, String path, int maxStringLength, Map<String, Object> info) {
        String text = node.textValue();
        int total = text.codePointCount(0, text.length());
        if (total <= maxStringLength) {
            return node;
        }
        int end = text.offsetByCodePoints(0, maxStringLength);
        record(
                info,
                path,
                "string",
                Map.of("original_length", total, "kept", maxStringLength));
        return NODES.textNode(text.substring(0, end));
    }

    private static void record(
            Map<String, Object> info, String path, String type, Map<String, Object> detail) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", type);
        entry.putAll(detail);
        info.put(path, entry);
    }

    private JsonNode tryParse(String content) {
        try {
            return JsonUtils.parseTree(content);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private Path writeFullResult(String content, PostToolCallContext context, Path output)
            throws IOException {
        Files.createDirectories(output);
        String fileName = sanitize(context.toolCall().name()) + "-" + UUID.randomUUID() + ".json";
        Path file = output.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private int resolveInt(Map<String, Object> options, String key, int fallback) {
        Object raw = options.get(key);
        if (raw instanceof Number number) {
            int value = number.intValue();
            if (value > 0) {
                return value;
            }
        }
        return fallback;
    }

    private Path resolveOutputDir(Map<String, Object> options) {
        Object raw = options.get(OPTION_OUTPUT_DIR);
        if (raw != null && !String.valueOf(raw).isBlank()) {
            return Path.of(String.valueOf(raw));
        }
        return outputDir;
    }

    private ContinuePostToolCall keep(PostToolCallContext context) {
        return new ContinuePostToolCall(
                HookMutations.none(),
                new ToolResultPatch(
                        context.toolResult().content(), context.toolResult().metadata()));
    }
}
