package org.gemo.apex.kit.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.JsonPathException;
import com.jayway.jsonpath.PathNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.gemo.apex.common.exception.JsonDecodingException;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolDefinition;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.extension.tool.AgentTool;
import org.gemo.apex.extension.tool.ToolExecutionObserver;
import org.gemo.apex.kit.artifact.ToolResultArtifactStore;
import org.gemo.apex.kit.hook.ToolResultTruncateHook;

/** 检查、搜索并定向读取当前 Session 中已落盘的完整工具结果。 */
public final class InspectToolResultTool implements AgentTool {
    public static final String NAME = "inspect_tool_result";
    public static final int MAX_TOOL_OUTPUT_CHARS = 20_000;

    private static final int DEFAULT_SLICE_CHARS = 8_000;
    private static final int MAX_SLICE_CHARS = 20_000;
    private static final int DEFAULT_SEARCH_RESULTS = 5;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int DEFAULT_SEARCH_CONTEXT_CHARS = 300;
    private static final int MAX_SEARCH_CONTEXT_CHARS = 2_000;
    private static final int DEFAULT_JSON_ARRAY_LIMIT = 20;
    private static final int MAX_JSON_ARRAY_LIMIT = 100;
    private static final int MAX_SELECTED_FIELDS = 50;
    private static final int MAX_STRUCTURE_SCAN_ITEMS = 50;
    private static final int MAX_STRUCTURE_FIELDS = 100;
    private static final int MAX_PATH_CHARS = 2_000;
    private static final int MAX_SEARCH_TEXT_CHARS = 2_000;
    private static final int MAX_SELECT_FIELD_CHARS = 256;
    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final ToolDefinition DEFINITION =
            new ToolDefinition(
                    NAME,
                    "检查之前因超长而落盘的完整工具结果。JSON 结构未知时先用 structure；已知 JSONPath 时用 json；文本先用 search 定位再用 slice。高级 JSONPath 会直接交给 Jayway 执行，但不承诺跨版本兼容。",
                    """
                    {"type":"object","additionalProperties":false,"required":["filename","operation"],"properties":{"filename":{"type":"string","maxLength":128,"description":"从之前截断工具响应的 _result.file 原样复制的文件名。"},"operation":{"type":"string","enum":["inspect","structure","slice","search","json"]},"path":{"type":"string","maxLength":2000,"description":"structure 或 json 使用的 Jayway JSONPath。"},"offset":{"type":"integer","minimum":0,"description":"slice 使用的 Java UTF-16 字符偏移。"},"limit":{"type":"integer","minimum":1,"maximum":20000},"search_text":{"type":"string","minLength":1,"maxLength":2000},"max_results":{"type":"integer","minimum":1,"maximum":20},"context_chars":{"type":"integer","minimum":0,"maximum":2000},"array_offset":{"type":"integer","minimum":0},"array_limit":{"type":"integer","minimum":1,"maximum":100},"select":{"type":"array","maxItems":50,"items":{"type":"string","minLength":1,"maxLength":256},"description":"对 JSONPath 结果中的对象做 dotted-field 投影，不改变记录集合和顺序。"}}}
                    """,
                    Map.of());

    private final ToolResultArtifactStore artifactStore;

    public InspectToolResultTool() {
        this(new ToolResultArtifactStore());
    }

    InspectToolResultTool(ToolResultArtifactStore artifactStore) {
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public ToolResult execute(
            ToolCall call, ToolExecutionContext context, ToolExecutionObserver observer) {
        if (!NAME.equals(call.name())) {
            throw new IllegalArgumentException("InspectToolResultTool 只能执行 inspect_tool_result 调用");
        }

        ParseOutcome parsed = parseRequest(call.arguments());
        if (parsed.error() != null) {
            return result(
                    call,
                    errorEnvelope(
                            parsed.filename(),
                            parsed.operation(),
                            "INVALID_OPERATION_ARGUMENTS",
                            parsed.error()));
        }
        Request request = parsed.request();
        Optional<ToolResultArtifactStore.ArtifactHandle> resolved =
                artifactStore.resolve(context.sharedData(), context.sessionId(), request.filename());
        if (resolved.isEmpty()) {
            return result(
                    call,
                    errorEnvelope(
                            request.filename(),
                            request.operation().value,
                            "FILE_NOT_FOUND",
                            "Artifact file was not found for the current session."));
        }

        context.cancellationToken().throwIfCancellationRequested();
        LoadedArtifact artifact;
        try {
            ToolResultArtifactStore.ArtifactHandle handle = resolved.get();
            long sizeBytes = Files.size(handle.path());
            String content = Files.readString(handle.path(), StandardCharsets.UTF_8);
            artifact = new LoadedArtifact(handle.contentType(), content, sizeBytes);
        } catch (IOException | SecurityException exception) {
            return result(
                    call,
                    errorEnvelope(
                            request.filename(),
                            request.operation().value,
                            "FILE_NOT_FOUND",
                            "Artifact file was not found for the current session."));
        }
        context.cancellationToken().throwIfCancellationRequested();

        Map<String, Object> envelope =
                switch (request.operation()) {
                    case INSPECT -> inspect(request, artifact);
                    case STRUCTURE -> structure(request, artifact);
                    case SLICE -> slice(request, artifact);
                    case SEARCH -> search(request, artifact, context);
                    case JSON -> json(request, artifact);
                };
        return result(call, envelope);
    }

    private Map<String, Object> inspect(Request request, LoadedArtifact artifact) {
        Map<String, Object> data = linkedMap();
        data.put("content_kind", "artifact_inspection");
        data.put("size_bytes", artifact.sizeBytes());
        if (isJson(artifact)) {
            JsonNode root;
            try {
                root = requireJson(artifact.content());
            } catch (InvalidJsonException exception) {
                return errorEnvelope(
                        request.filename(), request.operation().value, "INVALID_JSON", exception.getMessage());
            }
            data.put("detected_format", "json");
            data.put("root_type", nodeType(root));
            data.put("suggested_operations", List.of("structure", "json", "search"));
        } else {
            data.put("detected_format", "text");
            data.put("suggested_operations", List.of("search", "slice"));
        }
        return successEnvelope(request, data);
    }

    private Map<String, Object> structure(Request request, LoadedArtifact artifact) {
        JsonNode selected;
        try {
            selected = evaluateJsonPath(request, artifact);
        } catch (ToolOperationException exception) {
            return operationError(request, exception);
        }

        Map<String, Object> data = linkedMap();
        data.put("content_kind", "json_structure");
        data.put("path", request.path());
        data.putAll(describeStructure(selected));
        Map<String, Object> envelope = successEnvelope(request, data);
        trimStructureToFit(envelope, data);
        return envelope;
    }

    private Map<String, Object> slice(Request request, LoadedArtifact artifact) {
        String content = artifact.content();
        int requestedStart = Math.min(request.offset(), content.length());
        int start = requestedStart;
        if (start > 0 && start < content.length() && Character.isLowSurrogate(content.charAt(start))) {
            start++;
        }
        int requestedEnd = Math.min(content.length(), safeAdd(start, request.limit()));
        int low = start;
        int high = requestedEnd;
        int best = start;
        while (low <= high) {
            int midpoint = low + (high - low) / 2;
            int candidateEnd = safeSliceEnd(content, start, midpoint);
            Map<String, Object> envelope =
                    successEnvelope(
                            request,
                            sliceData(
                                    content,
                                    start,
                                    candidateEnd,
                                    requestedStart == start && candidateEnd == requestedEnd));
            if (serializedLength(envelope) <= MAX_TOOL_OUTPUT_CHARS) {
                best = Math.max(best, candidateEnd);
                low = midpoint + 1;
            } else {
                high = midpoint - 1;
            }
        }
        return successEnvelope(
                request,
                sliceData(
                        content,
                        start,
                        best,
                        requestedStart == start && best == requestedEnd));
    }

    private Map<String, Object> search(
            Request request, LoadedArtifact artifact, ToolExecutionContext context) {
        String content = artifact.content();
        List<Map<String, Object>> matches = new ArrayList<>();
        Map<String, Object> data = linkedMap();
        data.put("content_kind", "search_matches");
        data.put("search_text", request.searchText());
        data.put("matches", matches);

        int fromIndex = 0;
        boolean outputLimited = false;
        while (matches.size() < request.maxResults()) {
            context.cancellationToken().throwIfCancellationRequested();
            int index = content.indexOf(request.searchText(), fromIndex);
            if (index < 0) {
                fromIndex = content.length();
                break;
            }
            Map<String, Object> match = null;
            int low = 0;
            int high = request.contextChars();
            while (low <= high) {
                int contextChars = low + (high - low) / 2;
                Map<String, Object> candidate =
                        searchMatch(content, request.searchText(), index, contextChars);
                matches.add(candidate);
                updateSearchSummary(data, matches.size(), true, false);
                boolean fits = serializedLength(successEnvelope(request, data)) <= MAX_TOOL_OUTPUT_CHARS;
                matches.remove(matches.size() - 1);
                if (fits) {
                    match = candidate;
                    low = contextChars + 1;
                } else {
                    high = contextChars - 1;
                }
            }
            if (match == null) {
                outputLimited = true;
                break;
            }
            matches.add(match);
            fromIndex = index + request.searchText().length();
            if (high < request.contextChars()) {
                outputLimited = true;
                break;
            }
        }

        boolean moreMatches = outputLimited;
        if (!moreMatches && fromIndex < content.length()) {
            moreMatches = content.indexOf(request.searchText(), fromIndex) >= 0;
        }
        updateSearchSummary(data, matches.size(), moreMatches, outputLimited);
        return successEnvelope(request, data);
    }

    private Map<String, Object> json(Request request, LoadedArtifact artifact) {
        JsonNode selected;
        try {
            selected = evaluateJsonPath(request, artifact);
        } catch (ToolOperationException exception) {
            return operationError(request, exception);
        }

        ArraySelection arraySelection;
        try {
            arraySelection = applyArraySelection(selected, request);
            selected = applyProjection(arraySelection.value(), request.select());
        } catch (ToolOperationException exception) {
            return operationError(request, exception);
        }

        Map<String, Object> data = linkedMap();
        data.put("content_kind", "json_value");
        data.put("path", request.path());
        if (arraySelection.applied()) {
            data.put(
                    "selection",
                    Map.of(
                            "array_offset", arraySelection.offset(),
                            "array_limit", arraySelection.limit()));
            data.put("complete_for_requested_selection", true);
            data.put("has_more_after_selection", arraySelection.hasMore());
        } else if (!request.select().isEmpty()) {
            data.put("complete_for_requested_selection", true);
        } else {
            data.put("complete_for_requested_path", true);
        }
        if (!request.select().isEmpty()) {
            data.put("projection", Map.of("selected_fields", request.select()));
        }
        data.put("value", selected);

        Map<String, Object> envelope = successEnvelope(request, data);
        if (serializedLength(envelope) <= MAX_TOOL_OUTPUT_CHARS) {
            return envelope;
        }

        Map<String, Object> notReturned = linkedMap();
        notReturned.put("content_kind", "json_value_not_returned");
        notReturned.put("path", request.path());
        notReturned.put("complete_for_requested_path", false);
        notReturned.put("reason", "requested value exceeds tool output limit");
        notReturned.put("structure", describeStructure(selected));
        notReturned.put("suggested_next_actions", suggestedJsonActions(request, selected));
        envelope = successEnvelope(request, notReturned);
        trimStructureToFit(envelope, castMap(notReturned.get("structure")));
        return envelope;
    }

    private JsonNode evaluateJsonPath(Request request, LoadedArtifact artifact)
            throws ToolOperationException {
        if (!isJson(artifact)) {
            throw new ToolOperationException(
                    "UNSUPPORTED_FORMAT", "This operation requires a JSON artifact.");
        }
        try {
            requireJson(artifact.content());
            DocumentContext document = JsonPath.parse(artifact.content());
            Object value = document.read(request.path());
            JsonNode node = JsonUtils.toTree(value);
            return node == null ? NODES.nullNode() : node;
        } catch (InvalidJsonException exception) {
            throw new ToolOperationException("INVALID_JSON", exception.getMessage());
        } catch (PathNotFoundException exception) {
            throw new ToolOperationException(
                    "JSON_PATH_NOT_FOUND", "The requested JSONPath did not match a value.");
        } catch (InvalidPathException exception) {
            throw new ToolOperationException(
                    "INVALID_JSON_PATH", "The requested JSONPath is invalid.");
        } catch (JsonPathException exception) {
            throw new ToolOperationException(
                    "INVALID_JSON_PATH", "The requested JSONPath could not be evaluated.");
        } catch (RuntimeException exception) {
            throw new ToolOperationException(
                    "INVALID_JSON_PATH", "The requested JSONPath could not be evaluated.");
        }
    }

    private ArraySelection applyArraySelection(JsonNode selected, Request request)
            throws ToolOperationException {
        if (request.arrayOffset() == null && request.arrayLimit() == null) {
            return new ArraySelection(selected, false, 0, 0, false);
        }
        if (!selected.isArray()) {
            throw new ToolOperationException(
                    "JSON_TYPE_MISMATCH",
                    "array_offset and array_limit require an array JSONPath result.");
        }
        int offset = request.arrayOffset() == null ? 0 : request.arrayOffset();
        int limit = request.arrayLimit() == null ? DEFAULT_JSON_ARRAY_LIMIT : request.arrayLimit();
        int start = Math.min(offset, selected.size());
        int end = Math.min(selected.size(), safeAdd(start, limit));
        ArrayNode slice = NODES.arrayNode();
        for (int index = start; index < end; index++) {
            slice.add(selected.get(index).deepCopy());
        }
        return new ArraySelection(slice, true, offset, limit, end < selected.size());
    }

    private JsonNode applyProjection(JsonNode selected, List<String> fields)
            throws ToolOperationException {
        if (fields.isEmpty()) {
            return selected;
        }
        if (selected.isObject()) {
            return projectObject(selected, fields);
        }
        if (!selected.isArray()) {
            throw new ToolOperationException(
                    "JSON_TYPE_MISMATCH", "select requires an object or an array of objects.");
        }
        ArrayNode projected = NODES.arrayNode();
        for (JsonNode item : selected) {
            if (!item.isObject()) {
                throw new ToolOperationException(
                        "JSON_TYPE_MISMATCH", "select requires an array containing only objects.");
            }
            projected.add(projectObject(item, fields));
        }
        return projected;
    }

    private ObjectNode projectObject(JsonNode source, List<String> fields) {
        ObjectNode projected = NODES.objectNode();
        for (String field : fields) {
            String[] segments = field.split("\\.");
            JsonNode sourceValue = source;
            boolean found = true;
            for (String segment : segments) {
                sourceValue = sourceValue.isObject() ? sourceValue.get(segment) : null;
                if (sourceValue == null) {
                    found = false;
                    break;
                }
            }
            if (found) {
                putProjected(projected, segments, sourceValue.deepCopy());
            }
        }
        return projected;
    }

    private void putProjected(ObjectNode target, String[] segments, JsonNode value) {
        ObjectNode current = target;
        for (int index = 0; index < segments.length - 1; index++) {
            JsonNode existing = current.get(segments[index]);
            if (existing instanceof ObjectNode object) {
                current = object;
            } else {
                ObjectNode nested = NODES.objectNode();
                current.set(segments[index], nested);
                current = nested;
            }
        }
        current.set(segments[segments.length - 1], value);
    }

    private Map<String, Object> describeStructure(JsonNode node) {
        Map<String, Object> structure = linkedMap();
        structure.put("node_type", nodeType(node));
        if (node.isObject()) {
            Map<String, Object> fields = linkedMap();
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            int omitted = 0;
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> field = iterator.next();
                if (fields.size() < MAX_STRUCTURE_FIELDS) {
                    fields.put(field.getKey(), nodeType(field.getValue()));
                } else {
                    omitted++;
                }
            }
            structure.put("fields", fields);
            structure.put("complete_for_structure", omitted == 0);
            if (omitted > 0) {
                structure.put("fields_omitted", omitted);
            }
            return structure;
        }
        if (node.isArray()) {
            int scan = Math.min(node.size(), MAX_STRUCTURE_SCAN_ITEMS);
            structure.put(
                    "structure_inference", node.size() > scan ? "bounded" : "complete");
            structure.put("scanned_elements", scan);
            structure.put("has_unscanned_elements", node.size() > scan);
            structure.put("element_shape", arrayElementShape(node, scan));
        }
        return structure;
    }

    private Map<String, Object> arrayElementShape(JsonNode array, int scan) {
        Map<String, Object> shape = linkedMap();
        if (scan == 0) {
            shape.put("type", "unknown");
            return shape;
        }
        Set<String> types = new LinkedHashSet<>();
        Map<String, Set<String>> objectFields = new LinkedHashMap<>();
        int omittedFields = 0;
        for (int index = 0; index < scan; index++) {
            JsonNode item = array.get(index);
            String type = nodeType(item);
            types.add(type);
            if (item.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    if (objectFields.containsKey(field.getKey())
                            || objectFields.size() < MAX_STRUCTURE_FIELDS) {
                        objectFields
                                .computeIfAbsent(field.getKey(), ignored -> new LinkedHashSet<>())
                                .add(nodeType(field.getValue()));
                    } else {
                        omittedFields++;
                    }
                }
            }
        }
        if (types.size() == 1) {
            shape.put("type", types.iterator().next());
        } else {
            shape.put("types", List.copyOf(types));
        }
        if (!objectFields.isEmpty()) {
            Map<String, Object> fields = linkedMap();
            objectFields.forEach(
                    (name, fieldTypes) ->
                            fields.put(
                                    name,
                                    fieldTypes.size() == 1
                                            ? fieldTypes.iterator().next()
                                            : List.copyOf(fieldTypes)));
            shape.put("fields", fields);
            if (omittedFields > 0) {
                shape.put("fields_omitted", omittedFields);
            }
        }
        return shape;
    }

    private void trimStructureToFit(
            Map<String, Object> envelope, Map<String, Object> structureOwner) {
        while (serializedLength(envelope) > MAX_TOOL_OUTPUT_CHARS
                && removeLastStructureField(structureOwner)) {
            // 逐字段收窄，直到完整 envelope 满足预算。
        }
    }

    private boolean removeLastStructureField(Map<String, Object> owner) {
        Map<String, Object> fields = castMap(owner.get("fields"));
        Map<String, Object> fieldOwner = owner;
        if (fields == null) {
            Map<String, Object> elementShape = castMap(owner.get("element_shape"));
            if (elementShape != null) {
                fields = castMap(elementShape.get("fields"));
                fieldOwner = elementShape;
            }
        }
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        String last = null;
        for (String key : fields.keySet()) {
            last = key;
        }
        fields.remove(last);
        int omitted = numberValue(fieldOwner.get("fields_omitted"), 0) + 1;
        fieldOwner.put("fields_omitted", omitted);
        owner.put("complete_for_structure", false);
        return true;
    }

    private List<Map<String, Object>> suggestedJsonActions(Request request, JsonNode selected) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (selected.isArray()) {
            Map<String, Object> bounded = linkedMap();
            bounded.put("operation", "json");
            bounded.put("path", request.path());
            bounded.put("array_offset", 0);
            bounded.put("array_limit", DEFAULT_JSON_ARRAY_LIMIT);
            actions.add(bounded);
        }
        actions.add(Map.of("operation", "structure", "path", request.path()));
        return List.copyOf(actions);
    }

    private Map<String, Object> sliceData(
            String content, int start, int end, boolean completeForRequestedRange) {
        Map<String, Object> data = linkedMap();
        data.put("content_kind", "raw_slice");
        data.put("offset", start);
        data.put("returned_chars", end - start);
        data.put("complete_for_requested_range", completeForRequestedRange);
        data.put("has_more_after", end < content.length());
        if (end < content.length()) {
            data.put("next_offset", end);
        }
        data.put("content", content.substring(start, end));
        return data;
    }

    private Map<String, Object> searchMatch(
            String content, String searchText, int index, int contextChars) {
        int excerptStart = Math.max(0, index - contextChars);
        int excerptEnd = Math.min(content.length(), safeAdd(index + searchText.length(), contextChars));
        excerptStart = safeSliceStart(content, excerptStart);
        excerptEnd = safeSliceEnd(content, excerptStart, excerptEnd);
        Map<String, Object> match = linkedMap();
        match.put("offset", index);
        match.put("match_length", searchText.length());
        match.put("excerpt_start", excerptStart);
        match.put("excerpt_end", excerptEnd);
        match.put("excerpt", content.substring(excerptStart, excerptEnd));
        return match;
    }

    private void updateSearchSummary(
            Map<String, Object> data,
            int returnedMatches,
            boolean moreMatchesMayExist,
            boolean outputLimited) {
        data.put("returned_matches", returnedMatches);
        data.put("more_matches_may_exist", moreMatchesMayExist);
        data.put("output_limited", outputLimited);
    }

    private ParseOutcome parseRequest(Map<String, Object> arguments) {
        String filename = stringValue(arguments.get("filename"));
        String operationText = stringValue(arguments.get("operation"));
        Operation operation = Operation.from(operationText);
        if (filename == null || filename.isBlank()) {
            return ParseOutcome.error(filename, operationText, "filename is required.");
        }
        if (operation == null) {
            return ParseOutcome.error(filename, operationText, "operation is invalid.");
        }

        Set<String> allowed = new LinkedHashSet<>(Set.of("filename", "operation"));
        switch (operation) {
            case INSPECT -> {}
            case STRUCTURE -> allowed.add("path");
            case SLICE -> {
                allowed.add("offset");
                allowed.add("limit");
            }
            case SEARCH -> {
                allowed.add("search_text");
                allowed.add("max_results");
                allowed.add("context_chars");
            }
            case JSON -> {
                allowed.add("path");
                allowed.add("array_offset");
                allowed.add("array_limit");
                allowed.add("select");
            }
        }
        for (String key : arguments.keySet()) {
            if (!allowed.contains(key)) {
                return ParseOutcome.error(
                        filename, operationText, key + " is not valid for operation " + operationText + ".");
            }
        }

        try {
            String path = null;
            Integer offset = null;
            int limit = DEFAULT_SLICE_CHARS;
            String searchText = null;
            int maxResults = DEFAULT_SEARCH_RESULTS;
            int contextChars = DEFAULT_SEARCH_CONTEXT_CHARS;
            Integer arrayOffset = null;
            Integer arrayLimit = null;
            List<String> select = List.of();

            if (operation == Operation.STRUCTURE) {
                path = optionalString(arguments.get("path"), "$", "path");
                requireMaxLength(path, MAX_PATH_CHARS, "path");
            } else if (operation == Operation.SLICE) {
                offset = requiredInteger(arguments.get("offset"), 0, Integer.MAX_VALUE, "offset");
                limit = optionalInteger(arguments.get("limit"), DEFAULT_SLICE_CHARS, 1, MAX_SLICE_CHARS, "limit");
            } else if (operation == Operation.SEARCH) {
                searchText = requiredString(arguments.get("search_text"), "search_text");
                requireMaxLength(searchText, MAX_SEARCH_TEXT_CHARS, "search_text");
                maxResults = optionalInteger(arguments.get("max_results"), DEFAULT_SEARCH_RESULTS, 1, MAX_SEARCH_RESULTS, "max_results");
                contextChars = optionalInteger(arguments.get("context_chars"), DEFAULT_SEARCH_CONTEXT_CHARS, 0, MAX_SEARCH_CONTEXT_CHARS, "context_chars");
            } else if (operation == Operation.JSON) {
                path = requiredString(arguments.get("path"), "path");
                requireMaxLength(path, MAX_PATH_CHARS, "path");
                arrayOffset = optionalNullableInteger(arguments.get("array_offset"), 0, Integer.MAX_VALUE, "array_offset");
                arrayLimit = optionalNullableInteger(arguments.get("array_limit"), 1, MAX_JSON_ARRAY_LIMIT, "array_limit");
                select = parseSelect(arguments.get("select"));
            }
            return ParseOutcome.success(
                    new Request(
                            filename,
                            operation,
                            path,
                            offset,
                            limit,
                            searchText,
                            maxResults,
                            contextChars,
                            arrayOffset,
                            arrayLimit,
                            select));
        } catch (IllegalArgumentException exception) {
            return ParseOutcome.error(filename, operationText, exception.getMessage());
        }
    }

    private List<String> parseSelect(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> values) || values.size() > MAX_SELECTED_FIELDS) {
            throw new IllegalArgumentException("select must be an array with at most 50 fields.");
        }
        List<String> fields = new ArrayList<>();
        for (Object value : values) {
            String field = requiredString(value, "select field");
            requireMaxLength(field, MAX_SELECT_FIELD_CHARS, "select field");
            String[] segments = field.split("\\.", -1);
            for (String segment : segments) {
                if (segment.isBlank()) {
                    throw new IllegalArgumentException("select fields must use non-empty dotted segments.");
                }
            }
            if (!fields.contains(field)) {
                fields.add(field);
            }
        }
        return List.copyOf(fields);
    }

    private Map<String, Object> successEnvelope(Request request, Map<String, Object> data) {
        Map<String, Object> envelope = linkedMap();
        envelope.put("success", true);
        envelope.put("filename", request.filename());
        envelope.put("operation", request.operation().value);
        envelope.put("data", data);
        return envelope;
    }

    private Map<String, Object> errorEnvelope(
            String filename, String operation, String code, String message) {
        Map<String, Object> envelope = linkedMap();
        envelope.put("success", false);
        if (filename != null && !filename.isBlank()) {
            envelope.put("filename", filename);
        }
        if (operation != null && !operation.isBlank()) {
            envelope.put("operation", operation);
        }
        envelope.put("error", Map.of("code", code, "message", message));
        return envelope;
    }

    private Map<String, Object> operationError(Request request, ToolOperationException exception) {
        return errorEnvelope(
                request.filename(), request.operation().value, exception.code(), exception.getMessage());
    }

    private ToolResult result(ToolCall call, Map<String, Object> envelope) {
        String content = JsonUtils.toJson(envelope);
        if (content.length() > MAX_TOOL_OUTPUT_CHARS) {
            content =
                    JsonUtils.toJson(
                            errorEnvelope(
                                    stringValue(envelope.get("filename")),
                                    stringValue(envelope.get("operation")),
                                    "OUTPUT_LIMIT_EXCEEDED",
                                    "Inspector response exceeded its output limit."));
        }
        return new ToolResult(
                call.toolCallId(),
                call.name(),
                content,
                Map.of(ToolResultTruncateHook.BOUNDED_RESULT_METADATA_KEY, true));
    }

    private JsonNode requireJson(String content) throws InvalidJsonException {
        try {
            JsonNode node = JsonUtils.parseTree(content);
            if (node == null) {
                throw new InvalidJsonException("Artifact is empty and cannot be parsed as JSON.");
            }
            return node;
        } catch (JsonDecodingException exception) {
            throw new InvalidJsonException("Artifact is not valid JSON.");
        }
    }

    private boolean isJson(LoadedArtifact artifact) {
        return JSON_CONTENT_TYPE.equals(artifact.contentType());
    }

    private String nodeType(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return "unknown";
        }
        if (node.isObject()) {
            return "object";
        }
        if (node.isArray()) {
            return "array";
        }
        if (node.isTextual()) {
            return "string";
        }
        if (node.isNumber()) {
            return "number";
        }
        if (node.isBoolean()) {
            return "boolean";
        }
        if (node.isNull()) {
            return "null";
        }
        return "unknown";
    }

    private int safeSliceStart(String content, int start) {
        if (start > 0 && start < content.length() && Character.isLowSurrogate(content.charAt(start))) {
            return start + 1;
        }
        return start;
    }

    private int safeSliceEnd(String content, int start, int end) {
        int bounded = Math.max(start, Math.min(end, content.length()));
        if (bounded > start
                && bounded < content.length()
                && Character.isHighSurrogate(content.charAt(bounded - 1))
                && Character.isLowSurrogate(content.charAt(bounded))) {
            return bounded - 1;
        }
        return bounded;
    }

    private int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private int serializedLength(Map<String, Object> value) {
        return JsonUtils.toJson(value).length();
    }

    private String optionalString(Object raw, String defaultValue, String name) {
        return raw == null ? defaultValue : requiredString(raw, name);
    }

    private String requiredString(Object raw, String name) {
        if (!(raw instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return value;
    }

    private String stringValue(Object raw) {
        return raw instanceof String value ? value : null;
    }

    private void requireMaxLength(String value, int maximum, String name) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException(name + " exceeds the maximum length of " + maximum + ".");
        }
    }

    private int requiredInteger(Object raw, int minimum, int maximum, String name) {
        if (!(raw instanceof Number number)) {
            throw new IllegalArgumentException(name + " must be an integer.");
        }
        long value = number.longValue();
        if (number.doubleValue() != value || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be between " + minimum + " and " + maximum + ".");
        }
        return (int) value;
    }

    private int optionalInteger(
            Object raw, int defaultValue, int minimum, int maximum, String name) {
        return raw == null ? defaultValue : requiredInteger(raw, minimum, maximum, name);
    }

    private Integer optionalNullableInteger(Object raw, int minimum, int maximum, String name) {
        return raw == null ? null : requiredInteger(raw, minimum, maximum, name);
    }

    private int numberValue(Object raw, int defaultValue) {
        return raw instanceof Number number ? number.intValue() : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    private static Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    private enum Operation {
        INSPECT("inspect"),
        STRUCTURE("structure"),
        SLICE("slice"),
        SEARCH("search"),
        JSON("json");

        private final String value;

        Operation(String value) {
            this.value = value;
        }

        private static Operation from(String value) {
            for (Operation operation : values()) {
                if (operation.value.equals(value)) {
                    return operation;
                }
            }
            return null;
        }
    }

    private record Request(
            String filename,
            Operation operation,
            String path,
            Integer offset,
            int limit,
            String searchText,
            int maxResults,
            int contextChars,
            Integer arrayOffset,
            Integer arrayLimit,
            List<String> select) {}

    private record ParseOutcome(
            Request request, String filename, String operation, String error) {
        private static ParseOutcome success(Request request) {
            return new ParseOutcome(request, request.filename(), request.operation().value, null);
        }

        private static ParseOutcome error(String filename, String operation, String error) {
            return new ParseOutcome(null, filename, operation, error);
        }
    }

    private record LoadedArtifact(String contentType, String content, long sizeBytes) {}

    private record ArraySelection(
            JsonNode value, boolean applied, int offset, int limit, boolean hasMore) {}

    private static final class ToolOperationException extends Exception {
        private final String code;

        private ToolOperationException(String code, String message) {
            super(message);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private static final class InvalidJsonException extends Exception {
        private InvalidJsonException(String message) {
            super(message);
        }
    }
}
