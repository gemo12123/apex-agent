package org.gemo.apex.kit.artifact;

import java.util.Map;
import java.util.Optional;

/** 截断 Hook 与结果检查工具之间共享的已落盘工具结果描述符。 */
public record ToolResultArtifactDescriptor(String fileName, String path, String contentType) {
    public static final String SHARED_DATA_KEY = "apex.tool-result.artifacts";

    private static final String FILE_NAME_KEY = "fileName";
    private static final String PATH_KEY = "path";
    private static final String CONTENT_TYPE_KEY = "contentType";

    public ToolResultArtifactDescriptor {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("fileName 不能为空");
        }
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("contentType 不能为空");
        }
    }

    public Map<String, Object> toSharedDataValue() {
        return Map.of(FILE_NAME_KEY, fileName, PATH_KEY, path, CONTENT_TYPE_KEY, contentType);
    }

    public static Optional<ToolResultArtifactDescriptor> fromSharedDataValue(Object raw) {
        if (!(raw instanceof Map<?, ?> value)) {
            return Optional.empty();
        }
        Object fileName = value.get(FILE_NAME_KEY);
        Object path = value.get(PATH_KEY);
        Object contentType = value.get(CONTENT_TYPE_KEY);
        if (!(fileName instanceof String storedFileName)
                || !(path instanceof String storedPath)
                || !(contentType instanceof String storedContentType)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new ToolResultArtifactDescriptor(
                            storedFileName, storedPath, storedContentType));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
