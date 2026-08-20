package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.shared.SharedDataStore;
import org.gemo.apex.common.shared.SharedDataStores;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.common.tool.ToolExecutionContext;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.artifact.ToolResultArtifactStore;
import org.gemo.apex.kit.hook.ToolResultTruncateHook;
import org.gemo.apex.kit.tool.InspectToolResultTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InspectToolResultToolTest {
    private final InspectToolResultTool tool = new InspectToolResultTool();

    @Test
    void exposesBoundedInspectorDefinition() {
        JsonNode schema = JsonUtils.parseTree(tool.definition().inputSchemaJson());

        assertEquals(InspectToolResultTool.NAME, tool.definition().name());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertEquals(5, schema.get("properties").get("operation").get("enum").size());
    }

    @Test
    void inspectsJsonWithoutReturningBusinessPreview(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(4, 10));

        JsonNode response = fixture.execute(Map.of("operation", "inspect"));

        assertTrue(response.get("success").asBoolean());
        JsonNode data = response.get("data");
        assertEquals("artifact_inspection", data.get("content_kind").asText());
        assertEquals("json", data.get("detected_format").asText());
        assertEquals("object", data.get("root_type").asText());
        assertFalse(data.has("preview"));
        assertFalse(data.has("value"));
    }

    @Test
    void describesJsonPathStructureWithoutReturningValues(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(60, 5));

        JsonNode response =
                fixture.execute(
                        Map.of("operation", "structure", "path", "$.orders[*]"));

        assertTrue(response.get("success").asBoolean(), response.toString());
        JsonNode data = response.get("data");
        assertEquals("json_structure", data.get("content_kind").asText());
        assertEquals("array", data.get("node_type").asText());
        assertEquals("bounded", data.get("structure_inference").asText());
        assertEquals(50, data.get("scanned_elements").asInt());
        assertTrue(data.get("element_shape").get("fields").has("id"));
        assertFalse(data.has("value"));
    }

    @Test
    void allowsAdvancedJaywayJsonPathExpressions(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(5, 5));
        List<String> paths =
                List.of(
                        "$.orders[?(@.status == 'failed')]",
                        "$..status",
                        "$.orders[0,2]",
                        "$.orders.length()");

        for (String path : paths) {
            JsonNode response =
                    fixture.execute(Map.of("operation", "json", "path", path));
            assertTrue(response.get("success").asBoolean(), path + ": " + response);
            assertEquals("json_value", response.get("data").get("content_kind").asText());
        }
    }

    @Test
    void supportsGuaranteedJsonPathNavigationAndEmptyIndefiniteMatches(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(5, 5));

        JsonNode bracketField =
                fixture.execute(
                        Map.of("operation", "json", "path", "$['orders'][1]['status']"));
        JsonNode wildcard =
                fixture.execute(Map.of("operation", "json", "path", "$.orders[*].id"));
        JsonNode arraySlice =
                fixture.execute(Map.of("operation", "json", "path", "$.orders[1:3]"));
        JsonNode empty =
                fixture.execute(
                        Map.of("operation", "json", "path", "$.orders[?(@.id > 99)]"));

        assertEquals("success", bracketField.get("data").get("value").asText());
        assertEquals(5, wildcard.get("data").get("value").size());
        assertEquals(2, arraySlice.get("data").get("value").size());
        assertTrue(empty.get("data").get("value").isEmpty());
    }

    @Test
    void mapsInvalidAndMissingJsonPathsToStableErrors(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(2, 5));

        JsonNode invalid =
                fixture.execute(Map.of("operation", "json", "path", "$[?(@"));
        JsonNode missing =
                fixture.execute(Map.of("operation", "json", "path", "$.missing.value"));

        assertEquals("INVALID_JSON_PATH", invalid.get("error").get("code").asText());
        assertEquals("JSON_PATH_NOT_FOUND", missing.get("error").get("code").asText());
    }

    @Test
    void appliesPostJsonPathSliceAndDottedProjection(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(10, 50));

        JsonNode response =
                fixture.execute(
                        Map.of(
                                "operation",
                                "json",
                                "path",
                                "$.orders",
                                "array_offset",
                                2,
                                "array_limit",
                                3,
                                "select",
                                List.of("id", "status", "error.message")));

        assertTrue(response.get("success").asBoolean(), response.toString());
        JsonNode data = response.get("data");
        assertTrue(data.get("complete_for_requested_selection").asBoolean());
        assertTrue(data.get("has_more_after_selection").asBoolean());
        assertEquals(3, data.get("value").size());
        assertEquals(2, data.get("value").get(0).get("id").asInt());
        assertFalse(data.get("value").get(0).has("payload"));
        assertNotNull(data.get("value").get(0).get("error").get("message"));
    }

    @Test
    void reportsJsonSelectionTypeMismatches(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(2, 5));

        JsonNode arraySelectionOnObject =
                fixture.execute(
                        Map.of(
                                "operation",
                                "json",
                                "path",
                                "$",
                                "array_limit",
                                1));
        JsonNode projectionOnScalar =
                fixture.execute(
                        Map.of(
                                "operation",
                                "json",
                                "path",
                                "$.orders[0].id",
                                "select",
                                List.of("value")));

        assertEquals(
                "JSON_TYPE_MISMATCH",
                arraySelectionOnObject.get("error").get("code").asText());
        assertEquals(
                "JSON_TYPE_MISMATCH", projectionOnScalar.get("error").get("code").asText());
    }

    @Test
    void doesNotSilentlySliceOrReturnPartialJsonValue(@TempDir Path tempDir) {
        Fixture fixture = jsonFixture(tempDir, ordersJson(120, 500));

        JsonNode response =
                fixture.execute(Map.of("operation", "json", "path", "$.orders"));

        assertTrue(response.get("success").asBoolean(), response.toString());
        JsonNode data = response.get("data");
        assertEquals("json_value_not_returned", data.get("content_kind").asText());
        assertFalse(data.has("value"));
        assertFalse(data.get("complete_for_requested_path").asBoolean());
        assertTrue(response.toString().length() <= InspectToolResultTool.MAX_TOOL_OUTPUT_CHARS);
    }

    @Test
    void searchesTextThenReadsBoundedUnicodeSlice(@TempDir Path tempDir) {
        Fixture fixture = textFixture(tempDir, "开头😀\n" + "x".repeat(200) + "关键错误\n尾部");

        JsonNode searched =
                fixture.execute(
                        Map.of(
                                "operation",
                                "search",
                                "search_text",
                                "关键错误",
                                "context_chars",
                                10));
        int offset = searched.get("data").get("matches").get(0).get("offset").asInt();
        JsonNode sliced =
                fixture.execute(
                        Map.of("operation", "slice", "offset", offset, "limit", 20));

        assertEquals("search_matches", searched.get("data").get("content_kind").asText());
        assertTrue(
                searched
                        .get("data")
                        .get("matches")
                        .get(0)
                        .get("excerpt")
                        .asText()
                        .contains("关键错误"));
        assertEquals("raw_slice", sliced.get("data").get("content_kind").asText());
        assertTrue(sliced.get("data").get("content").asText().startsWith("关键错误"));
    }

    @Test
    void returnsEmptySlicePastEndAndNeverExceedsOutputBudget(@TempDir Path tempDir) {
        Fixture fixture = textFixture(tempDir, "\\\"😀".repeat(20_000));

        JsonNode pastEnd =
                fixture.execute(Map.of("operation", "slice", "offset", 1_000_000, "limit", 20));
        ToolResult bounded =
                fixture.executeRaw(
                        Map.of("operation", "slice", "offset", 0, "limit", 20_000));

        assertEquals(0, pastEnd.get("data").get("returned_chars").asInt());
        assertFalse(pastEnd.get("data").get("has_more_after").asBoolean());
        assertTrue(bounded.content().length() <= InspectToolResultTool.MAX_TOOL_OUTPUT_CHARS);
        assertTrue(
                Boolean.TRUE.equals(
                        bounded.metadata()
                                .get(ToolResultTruncateHook.BOUNDED_RESULT_METADATA_KEY)));
    }

    @Test
    void sliceBudgetSearchAlwaysProgressesAcrossSurrogatePair(@TempDir Path tempDir) {
        Fixture fixture = textFixture(tempDir, "a😀b");

        JsonNode response =
                fixture.execute(Map.of("operation", "slice", "offset", 0, "limit", 4));

        assertEquals("a😀b", response.get("data").get("content").asText());
        assertEquals(4, response.get("data").get("returned_chars").asInt());
    }

    @Test
    void rejectsArgumentsThatDoNotBelongToOperation(@TempDir Path tempDir) {
        Fixture fixture = textFixture(tempDir, "content");

        JsonNode response =
                fixture.execute(
                        Map.of("operation", "inspect", "search_text", "content"));

        assertEquals(
                "INVALID_OPERATION_ARGUMENTS", response.get("error").get("code").asText());
    }

    @Test
    void cannotReadUnregisteredOrOtherSessionArtifacts(@TempDir Path tempDir) throws Exception {
        Fixture fixture = textFixture(tempDir, "secret");
        Path unregistered =
                tempDir.resolve("session-1")
                        .resolve("tool-00000000-0000-0000-0000-000000000000.txt");
        Files.writeString(unregistered, "not registered");

        JsonNode unregisteredResponse =
                execute(
                        fixture.sharedData(),
                        "session-1",
                        unregistered.getFileName().toString(),
                        Map.of("operation", "inspect"));
        JsonNode otherSessionResponse =
                execute(
                        fixture.sharedData(),
                        "session-2",
                        fixture.fileName(),
                        Map.of("operation", "inspect"));

        assertEquals("FILE_NOT_FOUND", unregisteredResponse.get("error").get("code").asText());
        assertEquals("FILE_NOT_FOUND", otherSessionResponse.get("error").get("code").asText());
    }

    @Test
    void hidesTraversalAndDeletedArtifactsAsFileNotFound(@TempDir Path tempDir) throws Exception {
        Fixture fixture = textFixture(tempDir, "secret");
        Files.delete(tempDir.resolve("session-1").resolve(fixture.fileName()));

        JsonNode traversal =
                execute(
                        fixture.sharedData(),
                        "session-1",
                        "../source_tool-00000000-0000-0000-0000-000000000000.txt",
                        Map.of("operation", "inspect"));
        JsonNode deleted = fixture.execute(Map.of("operation", "inspect"));

        assertEquals("FILE_NOT_FOUND", traversal.get("error").get("code").asText());
        assertEquals("FILE_NOT_FOUND", deleted.get("error").get("code").asText());
    }

    @Test
    void hidesSymbolicLinkEscapeAsFileNotFound(@TempDir Path tempDir) throws Exception {
        Fixture fixture = textFixture(tempDir, "secret");
        Path artifact = tempDir.resolve("session-1").resolve(fixture.fileName());
        Path outside = tempDir.resolve("outside.txt");
        Files.writeString(outside, "outside secret");
        Files.delete(artifact);
        try {
            Files.createSymbolicLink(artifact, outside);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            assumeTrue(false, "当前文件系统不允许创建符号链接: " + exception.getMessage());
        }

        JsonNode response = fixture.execute(Map.of("operation", "inspect"));

        assertEquals("FILE_NOT_FOUND", response.get("error").get("code").asText());
    }

    private Fixture jsonFixture(Path root, String content) {
        return fixture(root, content, ".json", "application/json");
    }

    private Fixture textFixture(Path root, String content) {
        return fixture(root, content, ".txt", "text/plain");
    }

    private Fixture fixture(Path root, String content, String extension, String contentType) {
        SharedDataStore sharedData = SharedDataStores.create();
        ToolResultArtifactStore.StoreResult stored =
                new ToolResultArtifactStore()
                        .store(
                                sharedData,
                                "session-1",
                                root,
                                "source_tool",
                                extension,
                                contentType,
                                content);
        assertFalse(stored.failed());
        return new Fixture(sharedData, stored.fileName());
    }

    private JsonNode execute(
            SharedDataStore sharedData,
            String sessionId,
            String fileName,
            Map<String, Object> arguments) {
        Map<String, Object> merged = new java.util.LinkedHashMap<>(arguments);
        merged.put("filename", fileName);
        ToolExecutionContext context =
                new ToolExecutionContext(
                        sessionId,
                        1,
                        1,
                        "user-1",
                        null,
                        null,
                        KitFixtures.OBSERVER.cancellationToken(),
                        sharedData,
                        Map.of());
        ToolCall call = KitFixtures.call(InspectToolResultTool.NAME, merged);
        return JsonUtils.parseTree(tool.execute(call, context, KitFixtures.OBSERVER).content());
    }

    private String ordersJson(int count, int payloadSize) {
        StringBuilder json = new StringBuilder("{\"orders\":[");
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"id\":")
                    .append(index)
                    .append(",\"status\":\"")
                    .append(index % 2 == 0 ? "failed" : "success")
                    .append("\",\"payload\":\"")
                    .append("x".repeat(payloadSize))
                    .append("\",\"error\":{\"message\":\"error-")
                    .append(index)
                    .append("\"}}");
        }
        return json.append("]}").toString();
    }

    private final class Fixture {
        private final SharedDataStore sharedData;
        private final String fileName;

        private Fixture(SharedDataStore sharedData, String fileName) {
            this.sharedData = sharedData;
            this.fileName = fileName;
        }

        private JsonNode execute(Map<String, Object> arguments) {
            return JsonUtils.parseTree(executeRaw(arguments).content());
        }

        private ToolResult executeRaw(Map<String, Object> arguments) {
            Map<String, Object> merged = new java.util.LinkedHashMap<>(arguments);
            merged.put("filename", fileName);
            ToolCall call = KitFixtures.call(InspectToolResultTool.NAME, merged);
            return tool.execute(
                    call, KitFixtures.execution(sharedData, 1, 1), KitFixtures.OBSERVER);
        }

        private SharedDataStore sharedData() {
            return sharedData;
        }

        private String fileName() {
            return fileName;
        }
    }
}
