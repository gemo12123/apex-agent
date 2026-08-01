package org.gemo.apex.common.snapshot;

import com.fasterxml.jackson.databind.JsonNode;
import org.gemo.apex.common.exception.SnapshotDecodingException;
import org.gemo.apex.common.exception.UnsupportedSnapshotVersionException;
import org.gemo.apex.common.json.JsonUtils;

public final class SessionSnapshotJsonAdapter {
    public String write(SessionSnapshot snapshot) {
        return JsonUtils.toJson(snapshot);
    }

    public SessionSnapshot read(String json) {
        JsonNode tree;
        try {
            tree = JsonUtils.parseTree(json);
        } catch (RuntimeException exception) {
            throw new SnapshotDecodingException(null, null, exception);
        }
        String version = text(tree, "schemaVersion");
        if (!SnapshotSchemaVersion.V1.equals(version)) {
            throw new UnsupportedSnapshotVersionException(version);
        }
        String sessionId = text(tree, "sessionId");
        try {
            return JsonUtils.fromJson(json, SessionSnapshot.class);
        } catch (RuntimeException exception) {
            throw new SnapshotDecodingException(sessionId, version, exception);
        }
    }

    private static String text(JsonNode tree, String field) {
        return tree == null || tree.get(field) == null ? null : tree.get(field).asText();
    }
}
