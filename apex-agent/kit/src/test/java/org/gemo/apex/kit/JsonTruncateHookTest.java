package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.hook.context.PostToolCallContext;
import org.gemo.apex.common.hook.result.ContinuePostToolCall;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.tool.ToolResult;
import org.gemo.apex.kit.hook.JsonTruncateHook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonTruncateHookTest {
    /** 阈值内 JSON 原样返回 */
    @Test
    void keepsJsonWithinLimitUnchanged() {
        JsonTruncateHook hook = new JsonTruncateHook(100, 3, 10, 200, Path.of("."));
        ToolResult result = new ToolResult("call-1", "tool", "{\"a\":1}", Map.of());
        assertEquals(result.content(), apply(hook, result).patch().content());
    }

    /** 非 JSON 内容即使超长也原样返回 */
    @Test
    void keepsNonJsonUnchanged() {
        JsonTruncateHook hook = new JsonTruncateHook(10, 3, 10, 200, Path.of("."));
        ToolResult result = new ToolResult("call-1", "tool", "not json at all but long", Map.of());
        assertEquals(result.content(), apply(hook, result).patch().content());
    }

    /** 超长 JSON 返回信封：preview 合法、truncation_info 非空、完整原文落盘 */
    @Test
    void truncatesLargeJsonAndPersistsFullResult(@TempDir Path tempDir) throws Exception {
        String original =
                "{\"items\":[{\"a\":\"aaaaaaaaaa\"},{\"a\":\"bbbbbbbbbb\"},{\"a\":\"cccccccccc\"},"
                        + "{\"a\":\"dddddddddd\"},{\"a\":\"eeeeeeeeee\"}]}";
        JsonTruncateHook hook = new JsonTruncateHook(20, 2, 10, 200, tempDir);
        ToolResult result = new ToolResult("call-1", "tool", original, Map.of());

        JsonNode envelope = JsonUtils.parseTree(apply(hook, result).patch().content());
        assertTrue(envelope.get("truncated").asBoolean());
        assertTrue(envelope.get("truncation_info").size() > 0);
        assertEquals(2, envelope.get("data_preview").get("items").size());

        Path file = Path.of(envelope.get("full_result_file").get("file_path").asText());
        assertEquals(
                "application/json",
                envelope.get("full_result_file").get("content_type").asText());
        assertTrue(Files.exists(file));
        assertEquals(original, Files.readString(file));
        assertNotNull(JsonUtils.parseTree(Files.readString(file)));
    }

    /** 数组只保留前 N 个真实元素并记录原始长度 */
    @Test
    void truncatesArrayToSampleAndRecordsLength(@TempDir Path tempDir) {
        JsonTruncateHook hook = new JsonTruncateHook(10, 3, 10, 200, tempDir);
        JsonNode envelope =
                applyEnvelope(hook, "[{\"id\":1},{\"id\":2},{\"id\":3},{\"id\":4},{\"id\":5}]");

        JsonNode preview = envelope.get("data_preview");
        assertEquals(3, preview.size());
        assertEquals("{\"id\":1}", preview.get(0).toString());
        JsonNode info = envelope.get("truncation_info").get("$");
        assertEquals("array", info.get("type").asText());
        assertEquals(5, info.get("original_length").asInt());
        assertEquals(3, info.get("kept").asInt());
    }

    /** 对象字段过多时保留前 N 个字段并记录省略数量 */
    @Test
    void truncatesObjectFieldsAndRecordsOmitted(@TempDir Path tempDir) {
        JsonTruncateHook hook = new JsonTruncateHook(10, 3, 2, 200, tempDir);
        JsonNode envelope =
                applyEnvelope(hook, "{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":5}");

        JsonNode preview = envelope.get("data_preview");
        assertEquals(2, preview.size());
        assertTrue(preview.has("a"));
        assertTrue(preview.has("b"));
        JsonNode info = envelope.get("truncation_info").get("$");
        assertEquals("object", info.get("type").asText());
        assertEquals(5, info.get("original_fields").asInt());
        assertEquals(2, info.get("kept").asInt());
    }

    /** 超长字符串按码点截断且不切断 emoji */
    @Test
    void truncatesLongStringByCodePoint(@TempDir Path tempDir) {
        JsonTruncateHook hook = new JsonTruncateHook(10, 3, 10, 5, tempDir);
        JsonNode envelope = applyEnvelope(hook, "\"A😀BCDEFGHIJ\"");

        assertEquals("A😀BCD", envelope.get("data_preview").asText());
        JsonNode info = envelope.get("truncation_info").get("$");
        assertEquals("string", info.get("type").asText());
        assertEquals(11, info.get("original_length").asInt());
        assertEquals(5, info.get("kept").asInt());
    }

    /** 嵌套数组记录完整 JSONPath，preview 反序列化仍合法 */
    @Test
    void recordsNestedArrayJsonPathAndKeepsValidJson(@TempDir Path tempDir) {
        JsonTruncateHook hook = new JsonTruncateHook(10, 3, 10, 200, tempDir);
        StringBuilder json = new StringBuilder("{\"e\":[");
        for (int i = 0; i < 1000; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"k\":\"value-").append(i).append("\"}");
        }
        json.append("]}");
        JsonNode envelope = applyEnvelope(hook, json.toString());

        assertEquals(3, envelope.get("data_preview").get("e").size());
        JsonNode info = envelope.get("truncation_info").get("$.e");
        assertEquals("array", info.get("type").asText());
        assertEquals(1000, info.get("original_length").asInt());
        assertEquals(3, info.get("kept").asInt());
        assertNotNull(JsonUtils.parseTree(JsonUtils.toJson(envelope.get("data_preview"))));
    }

    /** binding options 覆盖默认 maxSize */
    @Test
    void readsMaxSizeOverrideFromBindingOptions(@TempDir Path tempDir) {
        JsonTruncateHook hook = new JsonTruncateHook();
        ToolResult result = new ToolResult("call-1", "tool", "{\"a\":\"short\"}", Map.of());
        PostToolCallContext context =
                new PostToolCallContext(
                        "session-1",
                        KitFixtures.binding(
                                "json",
                                List.of("*"),
                                Map.of("maxSize", 4, "outputDir", tempDir.toString())),
                        KitFixtures.call("tool", Map.of()),
                        result);
        ContinuePostToolCall patched =
                assertInstanceOf(ContinuePostToolCall.class, hook.apply(context));
        assertTrue(JsonUtils.parseTree(patched.patch().content()).get("truncated").asBoolean());
    }

    /** 非法长度在构造时失败 */
    @Test
    void rejectsInvalidValuesAtConstruction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JsonTruncateHook(0, 3, 10, 200, Path.of(".")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JsonTruncateHook(100, 0, 10, 200, Path.of(".")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JsonTruncateHook(100, 3, 0, 200, Path.of(".")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JsonTruncateHook(100, 3, 10, 0, Path.of(".")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new JsonTruncateHook(100, 3, 10, 200, null));
    }

    private JsonNode applyEnvelope(JsonTruncateHook hook, String content) {
        ToolResult result = new ToolResult("call-1", "tool", content, Map.of());
        return JsonUtils.parseTree(apply(hook, result).patch().content());
    }

    private ContinuePostToolCall apply(JsonTruncateHook hook, ToolResult result) {
        return assertInstanceOf(ContinuePostToolCall.class, hook.apply(KitFixtures.post(result)));
    }
}
