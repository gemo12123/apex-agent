package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.HookPoint;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataEntry;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.artifact.ToolResultArtifactDescriptor;
import org.gemo.apex.kit.hook.ToolResultTruncateHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolResultTruncateHookTest {
    private static final int MIN_BUDGET = ToolResultTruncateHook.MIN_MAX_SIZE;

    @Test
    void exposesNewRegistrationContract() {
        ToolResultTruncateHook hook = new ToolResultTruncateHook();

        assertEquals("toolResultTruncateHook", hook.name());
        assertEquals(HookPoint.POST_TOOL_CALL, hook.descriptor().hookPoint());
    }

    @Test
    void keepsContentWithinBudgetWithoutWritingFile(@TempDir Path tempDir) throws Exception {
        ToolResultTruncateHook hook = new ToolResultTruncateHook(MIN_BUDGET, tempDir);
        ToolResult result = result("{\"a\":1}");

        assertEquals(result.content(), apply(hook, result).patch().content());
        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void truncatesObjectArraysAndStringsWithinWholeEnvelopeBudget(@TempDir Path tempDir)
            throws Exception {
        StringBuilder original = new StringBuilder("{\"items\":[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                original.append(',');
            }
            original.append("{\"id\":").append(i).append(",\"value\":\"item\"}");
        }
        original.append("],\"description\":\"").append("😀说明".repeat(3000)).append("\"}");

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original.toString());

        assertTrue(envelope.get("data").isObject());
        assertTrue(envelope.get("data").get("items").size() <= 2);
        assertTrue(
                envelope.get("data").get("description").asText().length()
                        < "😀说明".repeat(3000).length());
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
        JsonNode metadata = envelope.get("_result");
        assertEquals("application/json", metadata.get("content_type").asText());
        assertTrue(metadata.get("file").asText().endsWith(".json"));
        assertEquals(1, Path.of(metadata.get("file").asText()).getNameCount());
        assertTrue(
                Files.exists(
                        tempDir.resolve("session-1").resolve(metadata.get("file").asText())));
    }

    @Test
    void unwrapsOneJsonStringLayerAsStructuredJson(@TempDir Path tempDir) throws Exception {
        String inner = "{\"items\":[{\"value\":\"" + "x".repeat(9000) + "\"}]}";
        String wrapped = JsonUtils.toJson(inner);

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), wrapped);

        assertTrue(envelope.get("data").isObject());
        assertEquals("application/json", envelope.get("_result").get("content_type").asText());
        Path storedFile =
                tempDir.resolve("session-1")
                        .resolve(envelope.get("_result").get("file").asText());
        String storedContent = Files.readString(storedFile);
        assertEquals(inner, storedContent);
        assertTrue(JsonUtils.parseTree(storedContent).isObject());
        assertEquals(
                wrapped.getBytes(StandardCharsets.UTF_8).length,
                envelope.get("_result").get("original_size_bytes").asLong());
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void doesNotUnwrapMoreThanOneJsonStringLayer(@TempDir Path tempDir) {
        String inner = "{\"value\":\"" + "x".repeat(9000) + "\"}";
        String twiceWrapped = JsonUtils.toJson(JsonUtils.toJson(inner));

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), twiceWrapped);

        assertTrue(envelope.get("data").isTextual());
        assertEquals("text/plain", envelope.get("_result").get("content_type").asText());
        assertTrue(envelope.get("_result").get("file").asText().endsWith(".txt"));
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void keepsOneObjectFromRootObjectArray(@TempDir Path tempDir) {
        StringBuilder original = new StringBuilder("[");
        for (int i = 0; i < 30; i++) {
            if (i > 0) {
                original.append(',');
            }
            original.append("{\"id\":")
                    .append(i)
                    .append(",\"payload\":\"")
                    .append("x".repeat(300))
                    .append("\"}");
        }
        original.append(']');

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original.toString());

        assertTrue(envelope.get("data").isArray());
        assertEquals(1, envelope.get("data").size());
        assertEquals(0, envelope.get("data").get(0).get("id").asInt());
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void reducesNestedContentWhenOneRootObjectStillExceedsBudget(@TempDir Path tempDir) {
        String original =
                "[{\"nested\":[{\"payload\":\""
                        + "😀".repeat(10000)
                        + "\"},{\"payload\":\"second\"}]}]";

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original);

        JsonNode data = envelope.get("data");
        assertTrue(data.isArray());
        assertEquals(1, data.size());
        assertEquals(1, data.get(0).get("nested").size());
        assertTrue(data.get(0).get("nested").get(0).get("payload").asText().length() < 10000);
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void treatsEmptyScalarAndMixedArraysAsStringPreview(@TempDir Path tempDir) {
        List<String> contents = List.of(
                "[]" + " ".repeat(5000),
                "[1,2,3," + "4,".repeat(3000) + "5]",
                "[{\"id\":1}," + "2,".repeat(3000) + "3]");
        ToolResultTruncateHook hook = new ToolResultTruncateHook(MIN_BUDGET, tempDir);

        for (String content : contents) {
            JsonNode envelope = applyEnvelope(hook, content);
            assertTrue(envelope.get("data").isTextual());
            assertEquals(
                    "application/json", envelope.get("_result").get("content_type").asText());
            assertTrue(envelope.get("_result").get("file").asText().endsWith(".json"));
            assertWithinBudget(envelope.toString(), MIN_BUDGET);
        }
    }

    @Test
    void fallsBackToTextForObjectWithoutReducibleValues(@TempDir Path tempDir) {
        StringBuilder original = new StringBuilder("{");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) {
                original.append(',');
            }
            original.append("\"numericField").append(i).append("\":").append(i);
        }
        original.append('}');

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original.toString());

        assertTrue(envelope.get("data").isTextual());
        assertTrue(envelope.get("data").asText().startsWith("{"));
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void truncatesJsonStringAsPlainTextWithoutSplittingEmoji(@TempDir Path tempDir) {
        String decoded = "A😀".repeat(5000);
        String original = JsonUtils.toJson(decoded);

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original);

        String preview = envelope.get("data").asText();
        assertTrue(decoded.startsWith(preview));
        assertFalse(preview.endsWith("\uD83D"));
        assertEquals("text/plain", envelope.get("_result").get("content_type").asText());
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void reportsUtf8BytesAndUsesTextExtension(@TempDir Path tempDir) {
        String original = "中文😀".repeat(3000);

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, tempDir), original);

        JsonNode metadata = envelope.get("_result");
        assertEquals(
                original.getBytes(StandardCharsets.UTF_8).length,
                metadata.get("original_size_bytes").asLong());
        assertTrue(metadata.get("file").asText().endsWith(".txt"));
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void returnsBudgetedEnvelopeWhenStorageFails(@TempDir Path tempDir) throws Exception {
        Path fileInsteadOfDirectory = tempDir.resolve("blocked");
        Files.writeString(fileInsteadOfDirectory, "not a directory");

        JsonNode envelope = applyEnvelope(
                new ToolResultTruncateHook(MIN_BUDGET, fileInsteadOfDirectory),
                "x".repeat(10000));

        JsonNode metadata = envelope.get("_result");
        assertTrue(metadata.get("storage_failed").asBoolean());
        assertFalse(metadata.has("file"));
        assertWithinBudget(envelope.toString(), MIN_BUDGET);
    }

    @Test
    void removesArtifactWhenSharedDataRegistrationFails(@TempDir Path tempDir) throws Exception {
        SharedDataStore rejectingStore =
                new SharedDataStore() {
                    @Override
                    public Object get(String key) {
                        return null;
                    }

                    @Override
                    public boolean containsKey(String key) {
                        return false;
                    }

                    @Override
                    public void put(String key, Object value, SharedDataCleanupPolicy cleanupPolicy) {
                        throw new IllegalStateException("shared data unavailable");
                    }

                    @Override
                    public void setCleanupPolicy(String key, SharedDataCleanupPolicy cleanupPolicy) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public SharedDataEntry remove(String key) {
                        return null;
                    }

                    @Override
                    public Map<String, SharedDataEntry> entries() {
                        return Map.of();
                    }
                };
        PostToolCallContext context =
                new PostToolCallContext(
                        "session-1",
                        KitFixtures.binding("truncate", List.of("*"), Map.of()),
                        KitFixtures.call("tool", Map.of()),
                        result("x".repeat(10000)),
                        rejectingStore);

        ContinuePostToolCall patched =
                assertInstanceOf(
                        ContinuePostToolCall.class,
                        new ToolResultTruncateHook(MIN_BUDGET, tempDir).apply(context));

        assertTrue(JsonUtils.parseTree(patched.patch().content()).get("_result").get("storage_failed").asBoolean());
        try (var files = Files.list(tempDir.resolve("session-1"))) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void sanitizesUnsafeSessionIdIntoOneSafeDirectoryLevel(@TempDir Path tempDir) {
        ToolResultTruncateHook hook = new ToolResultTruncateHook(MIN_BUDGET, tempDir);
        ToolResult result = result("x".repeat(10000));
        PostToolCallContext context =
                new PostToolCallContext(
                        "../unsafe/session",
                        KitFixtures.binding("truncate", List.of("*"), Map.of()),
                        KitFixtures.call("tool", Map.of()),
                        result);

        ContinuePostToolCall patched =
                assertInstanceOf(ContinuePostToolCall.class, hook.apply(context));
        JsonNode envelope = JsonUtils.parseTree(patched.patch().content());
        Path fileReference = Path.of(envelope.get("_result").get("file").asText());

        assertEquals(1, fileReference.getNameCount());
        assertFalse(fileReference.toString().contains("/"));
        assertFalse(fileReference.toString().contains("\\\\"));
        assertTrue(Files.exists(tempDir.resolve("___unsafe_session").resolve(fileReference)));
    }

    @Test
    void registersStoredArtifactInSessionSharedData(@TempDir Path tempDir) {
        SharedDataStore sharedData = SharedDataStores.create();
        ToolResult result = result("x".repeat(10000));
        PostToolCallContext context =
                new PostToolCallContext(
                        "session-1",
                        KitFixtures.binding("truncate", List.of("*"), Map.of()),
                        KitFixtures.call("tool", Map.of()),
                        result,
                        sharedData);

        ContinuePostToolCall patched =
                assertInstanceOf(
                        ContinuePostToolCall.class,
                        new ToolResultTruncateHook(MIN_BUDGET, tempDir).apply(context));
        String fileName =
                JsonUtils.parseTree(patched.patch().content())
                        .get("_result")
                        .get("file")
                        .asText();

        assertTrue(sharedData.containsKey(ToolResultArtifactDescriptor.SHARED_DATA_KEY));
        assertEquals(
                SharedDataCleanupPolicy.NEVER,
                sharedData.entries().get(ToolResultArtifactDescriptor.SHARED_DATA_KEY).cleanupPolicy());
        Map<?, ?> artifacts =
                assertInstanceOf(
                        Map.class, sharedData.get(ToolResultArtifactDescriptor.SHARED_DATA_KEY));
        ToolResultArtifactDescriptor descriptor =
                ToolResultArtifactDescriptor.fromSharedDataValue(artifacts.get(fileName)).orElseThrow();
        assertEquals(fileName, descriptor.fileName());
        assertEquals("text/plain", descriptor.contentType());
        assertEquals(
                tempDir.resolve("session-1").resolve(fileName).toAbsolutePath().normalize().toString(),
                descriptor.path());
    }

    @Test
    void doesNotTruncateAlreadyBoundedInspectorResult(@TempDir Path tempDir) throws Exception {
        ToolResult result =
                new ToolResult(
                        "call-1",
                        "inspect_tool_result",
                        "x".repeat(10000),
                        Map.of(ToolResultTruncateHook.BOUNDED_RESULT_METADATA_KEY, true));

        ContinuePostToolCall kept =
                apply(new ToolResultTruncateHook(MIN_BUDGET, tempDir), result);

        assertEquals(result.content(), kept.patch().content());
        try (var files = Files.list(tempDir)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void readsBindingOptionsAndRejectsInvalidBudgets(@TempDir Path tempDir) {
        ToolResultTruncateHook hook = new ToolResultTruncateHook();
        ToolResult result = result("x".repeat(10000));
        PostToolCallContext context =
                new PostToolCallContext(
                        "session-1",
                        KitFixtures.binding(
                                "truncate",
                                List.of("*"),
                                Map.of("maxSize", MIN_BUDGET, "outputDir", tempDir.toString())),
                        KitFixtures.call("tool", Map.of()),
                        result);

        ContinuePostToolCall patched =
                assertInstanceOf(ContinuePostToolCall.class, hook.apply(context));
        assertWithinBudget(patched.patch().content(), MIN_BUDGET);
        String fileName =
                JsonUtils.parseTree(patched.patch().content())
                        .get("_result")
                        .get("file")
                        .asText();
        assertTrue(Files.isRegularFile(tempDir.resolve("session-1").resolve(fileName)));
        Map<?, ?> artifacts =
                assertInstanceOf(
                        Map.class,
                        context.sharedData().get(ToolResultArtifactDescriptor.SHARED_DATA_KEY));
        assertTrue(artifacts.containsKey(fileName));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResultTruncateHook(MIN_BUDGET - 1, tempDir));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResultTruncateHook(MIN_BUDGET, null));

        PostToolCallContext invalidBinding =
                new PostToolCallContext(
                        "session-1",
                        KitFixtures.binding(
                                "truncate",
                                List.of("*"),
                                Map.of("maxSize", MIN_BUDGET - 1)),
                        KitFixtures.call("tool", Map.of()),
                        result);
        assertThrows(IllegalArgumentException.class, () -> hook.apply(invalidBinding));
    }

    private JsonNode applyEnvelope(ToolResultTruncateHook hook, String content) {
        return JsonUtils.parseTree(apply(hook, result(content)).patch().content());
    }

    private ContinuePostToolCall apply(ToolResultTruncateHook hook, ToolResult result) {
        return assertInstanceOf(ContinuePostToolCall.class, hook.apply(KitFixtures.post(result)));
    }

    private ToolResult result(String content) {
        return new ToolResult("call-1", "tool", content, Map.of());
    }

    private void assertWithinBudget(String content, int budget) {
        assertTrue(((long) content.length() + 3L) / 4L <= budget, content);
    }
}
