package org.gemo.apex.common;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.gemo.apex.common.execution.SessionStatus;
import org.gemo.apex.common.json.JsonUtils;
import org.gemo.apex.common.snapshot.SessionSnapshot;
import org.junit.jupiter.api.Test;

class JsonUtilsContractTest {
    /** 应支持泛型时间record枚举和树转换 */
    @Test
    void supportsGenericsTimeRecordsEnumsAndTreeConversion() {
        Map<String, List<Instant>> value = Map.of("times", List.of(CommonFixtures.NOW));
        Map<String, List<Instant>> copy =
                JsonUtils.fromJson(JsonUtils.toJson(value), new TypeReference<>() {});

        assertEquals(value, copy);
        assertEquals("CANCELLED", JsonUtils.toTree(SessionStatus.CANCELLED).asText());
        assertEquals(
                CommonFixtures.suspendedSnapshot(),
                JsonUtils.convert(
                        JsonUtils.toTree(CommonFixtures.suspendedSnapshot()),
                        SessionSnapshot.class));
    }

    /** deepCopy应断开嵌套集合双向别名 */
    @Test
    void deepCopyBreaksBidirectionalAliasingForNestedCollections() {
        List<Object> nested = new ArrayList<>(List.of("source"));
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("nested", nested);

        Map<String, Object> copy = JsonUtils.deepCopy(source, new TypeReference<>() {});
        nested.add("source-change");
        ((List<Object>) copy.get("nested")).add("copy-change");

        assertEquals(List.of("source", "source-change"), source.get("nested"));
        assertEquals(List.of("source", "copy-change"), copy.get("nested"));
    }

    /** mapperCopy的修改不得影响全局配置 */
    @Test
    void mapperCopyChangesDoNotAffectGlobalConfiguration() {
        JsonUtils.mapperCopy().enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        assertEquals("\"2026-08-01T08:00:00Z\"", JsonUtils.toJson(CommonFixtures.NOW));
    }

    /** null约定固定且抽象目标类型被拒绝 */
    @Test
    void nullContractIsFixedAndAbstractTargetTypesAreRejected() {
        assertNull(JsonUtils.toJson(null));
        assertNull(JsonUtils.fromJson(null, String.class));
        assertNull(JsonUtils.fromJson(" ", String.class));
        assertNull(JsonUtils.deepCopy(null, String.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> JsonUtils.deepCopy("value", CharSequence.class));
    }
}
