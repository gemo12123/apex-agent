package org.gemo.apex.kit.hook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.gemo.apex.common.exception.JsonDecodingException;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.HookTypeDescriptor;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.operation.HookMutations;
import org.gemo.apex.common.hook.operation.ToolResultPatch;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.hook.result.PostToolCallHookResult;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.extension.hook.LifecycleHook;
import org.gemo.apex.kit.artifact.ToolResultArtifactDescriptor;

/** 对超预算工具结果做 JSON 感知截断，并把完整可复用正文落盘。 */
public final class ToolResultTruncateHook
        implements LifecycleHook<PostToolCallContext, PostToolCallHookResult> {
    public static final String REGISTRATION_NAME = "toolResultTruncateHook";
    public static final String OPTION_MAX_SIZE = "maxSize";
    public static final String OPTION_OUTPUT_DIR = "outputDir";
    public static final String BOUNDED_RESULT_METADATA_KEY = "apex.tool-result.bounded";
    public static final int DEFAULT_MAX_SIZE = 8000;
    public static final int MIN_MAX_SIZE = 1024;

    private static final int OBJECT_ARRAY_SAMPLE_SIZE = 2;
    private static final int ROOT_ARRAY_SAMPLE_SIZE = 1;
    private static final int MAX_FILE_PREFIX_CODE_POINTS = 64;
    private static final int MAX_SESSION_DIRECTORY_CODE_POINTS = 128;
    private static final int MAX_TRUNCATION_ENTRIES = 8;
    private static final int MAX_TRUNCATION_PATH_CODE_POINTS = 128;
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final HookTypeDescriptor DESCRIPTOR =
            new HookTypeDescriptor(
                    HookPoint.POST_TOOL_CALL,
                    PostToolCallContext.class,
                    PostToolCallHookResult.class);

    private final int maxSize;
    private final Path outputDir;

    public ToolResultTruncateHook() {
        this(
                DEFAULT_MAX_SIZE,
                Path.of(System.getProperty("java.io.tmpdir"), "apex-tool-result-truncate"));
    }

    public ToolResultTruncateHook(int maxSize, Path outputDir) {
        this.maxSize = requireMaxSize(maxSize);
        if (outputDir == null) {
            throw new IllegalArgumentException("outputDir 不能为空");
        }
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
        if (Boolean.TRUE.equals(
                context.toolResult().metadata().get(BOUNDED_RESULT_METADATA_KEY))) {
            return keep(context);
        }
        String content = context.toolResult().content();
        Map<String, Object> options = context.binding().options();
        int budget = resolveMaxSize(options);
        if (estimateTokens(content) <= budget) {
            return keep(context);
        }

        ParsedContent parsed = parseContent(content);
        String contentType = parsed.structured() == null ? TEXT_CONTENT_TYPE : JSON_CONTENT_TYPE;
        String extension = parsed.structured() == null ? ".txt" : ".json";
        StorageResult storage =
                writeFullResult(
                        parsed.persistedContent(),
                        context.sessionId(),
                        context.toolCall().name(),
                        extension,
                        resolveOutputDir(options),
                        context.sharedData(),
                        contentType);
        EnvelopeMetadata metadata =
                new EnvelopeMetadata(
                        storage.fileName(),
                        storage.failed(),
                        contentType,
                        content.getBytes(StandardCharsets.UTF_8).length);

        String patched =
                parsed.structured() == null
                        ? fitTextEnvelope(parsed.text(), metadata, budget)
                        : fitStructuredEnvelope(parsed.structured(), content, metadata, budget);
        if (estimateTokens(patched) > budget) {
            throw new IllegalStateException("工具结果截断后仍超过 maxSize 预算");
        }
        return new ContinuePostToolCall(
                HookMutations.none(),
                new ToolResultPatch(patched, context.toolResult().metadata()));
    }

    private String fitStructuredEnvelope(
            JsonNode root, String originalContent, EnvelopeMetadata metadata, int budget) {
        List<Truncation> truncations = new ArrayList<>();
        if (root.isObject()) {
            JsonNode data = root.deepCopy();
            if (fits(data, metadata, truncations, budget)) {
                return serializeEnvelope(data, metadata, truncations);
            }
            if (reduceToFit(
                            data,
                            data,
                            "$",
                            OBJECT_ARRAY_SAMPLE_SIZE,
                            metadata,
                            truncations,
                            budget)
                    || reduceToFit(
                            data,
                            data,
                            "$",
                            ROOT_ARRAY_SAMPLE_SIZE,
                            metadata,
                            truncations,
                            budget)) {
                return serializeEnvelope(data, metadata, truncations);
            }
            return fitTextEnvelope(originalContent, metadata, budget);
        }

        if (isObjectArray(root)) {
            ArrayNode data = NODES.arrayNode();
            data.add(root.get(0).deepCopy());
            if (root.size() > ROOT_ARRAY_SAMPLE_SIZE) {
                truncations.add(
                        new Truncation(
                                "$",
                                "array",
                                root.size(),
                                ROOT_ARRAY_SAMPLE_SIZE));
            }
            if (fits(data, metadata, truncations, budget)
                    || reduceToFit(
                            data,
                            data.get(0),
                            "$[0]",
                            ROOT_ARRAY_SAMPLE_SIZE,
                            metadata,
                            truncations,
                            budget)) {
                return serializeEnvelope(data, metadata, truncations);
            }
        }
        return fitTextEnvelope(originalContent, metadata, budget);
    }

    private boolean reduceToFit(
            JsonNode envelopeData,
            JsonNode searchRoot,
            String rootPath,
            int arrayLimit,
            EnvelopeMetadata metadata,
            List<Truncation> truncations,
            int budget) {
        while (!fits(envelopeData, metadata, truncations, budget)) {
            Candidate candidate = findLargestCandidate(searchRoot, rootPath, arrayLimit);
            if (candidate == null) {
                return false;
            }
            if (candidate.node().isArray()) {
                ArrayNode array = (ArrayNode) candidate.node();
                int originalLength = array.size();
                while (array.size() > arrayLimit) {
                    array.remove(array.size() - 1);
                }
                truncations.add(
                        new Truncation(
                                candidate.path(),
                                "array",
                                originalLength,
                                array.size()));
            } else {
                shrinkStringToFit(
                        envelopeData,
                        candidate,
                        metadata,
                        truncations,
                        budget);
            }
        }
        return true;
    }

    private void shrinkStringToFit(
            JsonNode envelopeData,
            Candidate candidate,
            EnvelopeMetadata metadata,
            List<Truncation> truncations,
            int budget) {
        String original = candidate.node().textValue();
        int total = original.codePointCount(0, original.length());
        int low = 0;
        int high = total - 1;
        int best = -1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            replaceCandidate(candidate, prefixByCodePoints(original, middle));
            List<Truncation> pending = withTruncation(
                    truncations,
                    new Truncation(candidate.path(), "string", total, middle));
            if (fits(envelopeData, metadata, pending, budget)) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        int kept = Math.max(0, best);
        replaceCandidate(candidate, prefixByCodePoints(original, kept));
        truncations.add(
                new Truncation(candidate.path(), "string", total, kept));
    }

    private Candidate findLargestCandidate(JsonNode root, String rootPath, int arrayLimit) {
        List<Candidate> candidates = new ArrayList<>();
        collectCandidates(root, rootPath, null, null, -1, arrayLimit, candidates);
        Candidate largestArray = null;
        Candidate largestString = null;
        for (Candidate candidate : candidates) {
            if (candidate.node().isArray()) {
                if (largestArray == null
                        || candidate.serializedSize() > largestArray.serializedSize()) {
                    largestArray = candidate;
                }
            } else if (largestString == null
                    || candidate.serializedSize() > largestString.serializedSize()) {
                largestString = candidate;
            }
        }
        return largestArray == null ? largestString : largestArray;
    }

    private void collectCandidates(
            JsonNode node,
            String path,
            JsonNode parent,
            String fieldName,
            int index,
            int arrayLimit,
            List<Candidate> candidates) {
        if (node.isArray()) {
            if (node.size() > arrayLimit) {
                candidates.add(
                        new Candidate(
                                parent,
                                fieldName,
                                index,
                                node,
                                path,
                                JsonUtils.toJson(node).length()));
            }
            for (int i = 0; i < node.size(); i++) {
                collectCandidates(
                        node.get(i),
                        path + "[" + i + "]",
                        node,
                        null,
                        i,
                        arrayLimit,
                        candidates);
            }
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectCandidates(
                        field.getValue(),
                        appendPath(path, field.getKey()),
                        node,
                        field.getKey(),
                        -1,
                        arrayLimit,
                        candidates);
            }
            return;
        }
        if (node.isTextual() && !node.textValue().isEmpty()) {
            candidates.add(
                    new Candidate(
                            parent,
                            fieldName,
                            index,
                            node,
                            path,
                            JsonUtils.toJson(node).length()));
        }
    }

    private String fitTextEnvelope(String text, EnvelopeMetadata metadata, int budget) {
        int total = text.codePointCount(0, text.length());
        int low = 0;
        int high = total;
        int best = -1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            String preview = prefixByCodePoints(text, middle);
            List<Truncation> truncations =
                    List.of(new Truncation("$", "string", total, middle));
            if (fits(NODES.textNode(preview), metadata, truncations, budget)) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (best < 0) {
            throw new IllegalStateException("maxSize 无法容纳最小截断信封");
        }
        String preview = prefixByCodePoints(text, best);
        return serializeEnvelope(
                NODES.textNode(preview),
                metadata,
                List.of(new Truncation("$", "string", total, best)));
    }

    private boolean fits(
            JsonNode data,
            EnvelopeMetadata metadata,
            List<Truncation> truncations,
            int budget) {
        return estimateTokens(serializeEnvelope(data, metadata, truncations)) <= budget;
    }

    private String serializeEnvelope(
            JsonNode data, EnvelopeMetadata metadata, List<Truncation> truncations) {
        ObjectNode envelope = NODES.objectNode();
        envelope.set("data", data);

        ObjectNode result = NODES.objectNode();
        result.put("truncated", true);
        if (metadata.storageFailed()) {
            result.put("storage_failed", true);
            result.put("storage_error", "文件存储异常，原文未落盘");
        } else {
            result.put("file", metadata.fileName());
        }
        result.put("content_type", metadata.contentType());
        result.put("original_size_bytes", metadata.originalSizeBytes());

        int emitted = Math.min(truncations.size(), MAX_TRUNCATION_ENTRIES);
        if (emitted > 0) {
            ArrayNode entries = NODES.arrayNode();
            for (int i = 0; i < emitted; i++) {
                Truncation truncation = truncations.get(i);
                ObjectNode entry = NODES.objectNode();
                entry.put("path", limitCodePoints(truncation.path(), MAX_TRUNCATION_PATH_CODE_POINTS));
                entry.put("type", truncation.type());
                entry.put("original_length", truncation.originalLength());
                entry.put("kept", truncation.kept());
                entries.add(entry);
            }
            result.set("truncations", entries);
        }
        if (truncations.size() > emitted) {
            result.put("truncations_omitted", truncations.size() - emitted);
        }
        envelope.set("_result", result);
        return JsonUtils.toJson(envelope);
    }

    private ParsedContent parseContent(String content) {
        JsonNode first = tryParse(content);
        if (first != null && (first.isObject() || first.isArray())) {
            return new ParsedContent(first, null, content);
        }
        if (first != null && first.isTextual()) {
            String decoded = first.asText();
            JsonNode second = tryParse(decoded);
            if (second != null && (second.isObject() || second.isArray())) {
                return new ParsedContent(second, null, decoded);
            }
            return new ParsedContent(null, decoded, content);
        }
        return new ParsedContent(null, content, content);
    }

    private JsonNode tryParse(String content) {
        try {
            return JsonUtils.parseTree(content);
        } catch (JsonDecodingException exception) {
            return null;
        }
    }

    private int resolveMaxSize(Map<String, Object> options) {
        Object raw = options.get(OPTION_MAX_SIZE);
        if (raw == null) {
            return maxSize;
        }
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException("maxSize 必须是数字");
        }
        return requireMaxSize(number.intValue());
    }

    private Path resolveOutputDir(Map<String, Object> options) {
        Object raw = options.get(OPTION_OUTPUT_DIR);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return outputDir;
        }
        return Path.of(String.valueOf(raw));
    }

    private StorageResult writeFullResult(
            String content,
            String sessionId,
            String toolName,
            String extension,
            Path outputDirectory,
            SharedDataStore sharedData,
            String contentType) {
        Path createdFile = null;
        try {
            String sessionDirectoryName = sanitizeSessionDirectoryName(sessionId);
            Path sessionDirectory = outputDirectory.resolve(sessionDirectoryName);
            Files.createDirectories(sessionDirectory);
            String prefix = limitCodePoints(sanitizeToolName(toolName), MAX_FILE_PREFIX_CODE_POINTS);
            String fileName = prefix + "-" + UUID.randomUUID() + extension;
            createdFile = sessionDirectory.resolve(fileName);
            Files.writeString(
                    createdFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            registerArtifact(sharedData, fileName, createdFile, contentType);
            return new StorageResult(fileName, false);
        } catch (IOException | RuntimeException exception) {
            deleteOrphan(createdFile);
            return new StorageResult(null, true);
        }
    }

    private void registerArtifact(
            SharedDataStore sharedData,
            String fileName,
            Path artifactPath,
            String contentType) {
        Map<String, Object> artifacts = new LinkedHashMap<>();
        Object raw = sharedData.get(ToolResultArtifactDescriptor.SHARED_DATA_KEY);
        if (raw instanceof Map<?, ?> existing) {
            existing.forEach(
                    (key, value) -> {
                        if (key instanceof String name) {
                            artifacts.put(name, value);
                        }
                    });
        }
        artifacts.put(
                fileName,
                new ToolResultArtifactDescriptor(
                                fileName,
                                artifactPath.toAbsolutePath().normalize().toString(),
                                contentType)
                        .toSharedDataValue());
        sharedData.put(
                ToolResultArtifactDescriptor.SHARED_DATA_KEY,
                artifacts,
                SharedDataCleanupPolicy.NEVER);
    }

    private static void deleteOrphan(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException ignored) {
            // 无法登记的孤立文件不影响截断信封返回。
        }
    }

    private static int requireMaxSize(int value) {
        if (value < MIN_MAX_SIZE) {
            throw new IllegalArgumentException("maxSize 必须 >= " + MIN_MAX_SIZE);
        }
        return value;
    }

    private static long estimateTokens(String content) {
        return ((long) content.length() + 3L) / 4L;
    }

    private static boolean isObjectArray(JsonNode node) {
        if (!node.isArray() || node.isEmpty()) {
            return false;
        }
        for (JsonNode element : node) {
            if (!element.isObject()) {
                return false;
            }
        }
        return true;
    }

    private static void replaceCandidate(Candidate candidate, String value) {
        if (candidate.parent() instanceof ObjectNode object) {
            object.put(candidate.fieldName(), value);
            return;
        }
        if (candidate.parent() instanceof ArrayNode array) {
            array.set(candidate.index(), NODES.textNode(value));
            return;
        }
        throw new IllegalStateException("字符串候选节点缺少父节点");
    }

    private static List<Truncation> withTruncation(
            List<Truncation> truncations, Truncation pending) {
        List<Truncation> result = new ArrayList<>(truncations.size() + 1);
        result.addAll(truncations);
        result.add(pending);
        return result;
    }

    private static String prefixByCodePoints(String value, int length) {
        if (length <= 0) {
            return "";
        }
        int total = value.codePointCount(0, value.length());
        if (length >= total) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, length));
    }

    private static String limitCodePoints(String value, int maxLength) {
        return prefixByCodePoints(value, maxLength);
    }

    private static String sanitizeToolName(String name) {
        String sanitized = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "tool" : sanitized;
    }

    private static String sanitizeSessionDirectoryName(String sessionId) {
        String sanitized = sessionId.replaceAll("[^A-Za-z0-9_-]", "_");
        sanitized = limitCodePoints(sanitized, MAX_SESSION_DIRECTORY_CODE_POINTS);
        return sanitized.isBlank() ? "session" : sanitized;
    }

    private static String appendPath(String path, String fieldName) {
        if (fieldName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return path + "." + fieldName;
        }
        return path + "['" + fieldName.replace("'", "\\'") + "']";
    }

    private ContinuePostToolCall keep(PostToolCallContext context) {
        return new ContinuePostToolCall(
                HookMutations.none(),
                new ToolResultPatch(
                        context.toolResult().content(), context.toolResult().metadata()));
    }

    private record ParsedContent(JsonNode structured, String text, String persistedContent) {}

    private record StorageResult(String fileName, boolean failed) {}

    private record EnvelopeMetadata(
            String fileName,
            boolean storageFailed,
            String contentType,
            long originalSizeBytes) {}

    private record Truncation(String path, String type, int originalLength, int kept) {}

    private record Candidate(
            JsonNode parent,
            String fieldName,
            int index,
            JsonNode node,
            String path,
            int serializedSize) {}
}
