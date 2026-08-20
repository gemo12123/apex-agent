package org.gemo.apex.kit.artifact;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.gemo.apex.common.shared.SharedDataCleanupPolicy;
import org.gemo.apex.common.shared.SharedDataStore;

/** 管理截断工具结果的本地落盘、Session 级登记与安全解析。 */
public final class ToolResultArtifactStore {
    public static final String SHARED_DATA_KEY = "apex.tool-result.artifacts";

    private static final Pattern SESSION_DIRECTORY_PATTERN =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile(
                    "[A-Za-z0-9._-]{1,64}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(json|txt)");
    private static final int MAX_TOOL_NAME_CODE_POINTS = 64;

    public StoreResult store(
            SharedDataStore sharedData,
            String sessionId,
            Path outputDirectory,
            String toolName,
            String extension,
            String contentType,
            String content) {
        if (sharedData == null
                || outputDirectory == null
                || !isSafeSessionDirectory(sessionId)
                || !(".json".equals(extension) || ".txt".equals(extension))) {
            return StoreResult.failure();
        }

        Path createdFile = null;
        try {
            Path normalizedRoot = outputDirectory.toAbsolutePath().normalize();
            Path sessionDirectory = normalizedRoot.resolve(sessionId).normalize();
            if (!sessionDirectory.getParent().equals(normalizedRoot)) {
                return StoreResult.failure();
            }
            Files.createDirectories(sessionDirectory);

            String prefix = limitCodePoints(sanitizeToolName(toolName), MAX_TOOL_NAME_CODE_POINTS);
            String fileName = prefix + "-" + UUID.randomUUID() + extension;
            createdFile = sessionDirectory.resolve(fileName);
            Files.writeString(
                    createdFile,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            register(sharedData, fileName, normalizedRoot, contentType);
            return new StoreResult(fileName, false);
        } catch (IOException | RuntimeException exception) {
            deleteOrphan(createdFile);
            return StoreResult.failure();
        }
    }

    public Optional<ArtifactHandle> resolve(
            SharedDataStore sharedData, String sessionId, String fileName) {
        if (sharedData == null
                || !isSafeSessionDirectory(sessionId)
                || !isSafeFileName(fileName)) {
            return Optional.empty();
        }
        ArtifactRegistration registration = registration(sharedData, fileName);
        if (registration == null) {
            return Optional.empty();
        }

        try {
            Path root = Path.of(registration.outputDirectory()).toAbsolutePath().normalize();
            Path sessionDirectory = root.resolve(sessionId).normalize();
            Path candidate = sessionDirectory.resolve(fileName).normalize();
            if (!sessionDirectory.getParent().equals(root)
                    || !candidate.getParent().equals(sessionDirectory)
                    || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }

            Path realSessionDirectory = sessionDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path realCandidate = candidate.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!realCandidate.getParent().equals(realSessionDirectory)
                    || !Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            return Optional.of(
                    new ArtifactHandle(realCandidate, registration.contentType(), fileName));
        } catch (IOException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static boolean isSafeSessionDirectory(String sessionId) {
        return sessionId != null && SESSION_DIRECTORY_PATTERN.matcher(sessionId).matches();
    }

    public static boolean isSafeFileName(String fileName) {
        if (fileName == null || !FILE_NAME_PATTERN.matcher(fileName).matches()) {
            return false;
        }
        try {
            Path path = Path.of(fileName);
            return !path.isAbsolute()
                    && path.getNameCount() == 1
                    && path.getFileName().toString().equals(fileName);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void register(
            SharedDataStore sharedData, String fileName, Path outputDirectory, String contentType) {
        Map<String, Object> registrations = new LinkedHashMap<>();
        Object raw = sharedData.get(SHARED_DATA_KEY);
        if (raw instanceof Map<?, ?> existing) {
            existing.forEach(
                    (key, value) -> {
                        if (key instanceof String name) {
                            registrations.put(name, value);
                        }
                    });
        }
        registrations.put(
                fileName,
                Map.of(
                        "outputDirectory", outputDirectory.toString(),
                        "contentType", contentType));
        sharedData.put(SHARED_DATA_KEY, registrations, SharedDataCleanupPolicy.NEVER);
    }

    private ArtifactRegistration registration(SharedDataStore sharedData, String fileName) {
        Object raw = sharedData.get(SHARED_DATA_KEY);
        if (!(raw instanceof Map<?, ?> registrations)) {
            return null;
        }
        Object rawRegistration = registrations.get(fileName);
        if (!(rawRegistration instanceof Map<?, ?> registration)) {
            return null;
        }
        Object outputDirectory = registration.get("outputDirectory");
        Object contentType = registration.get("contentType");
        if (!(outputDirectory instanceof String directory)
                || directory.isBlank()
                || !(contentType instanceof String type)
                || type.isBlank()) {
            return null;
        }
        return new ArtifactRegistration(directory, type);
    }

    private static String sanitizeToolName(String name) {
        String sanitized = name == null ? "" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "tool" : sanitized;
    }

    private static String limitCodePoints(String value, int maxLength) {
        int total = value.codePointCount(0, value.length());
        if (total <= maxLength) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxLength));
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

    public record StoreResult(String fileName, boolean failed) {
        public static StoreResult failure() {
            return new StoreResult(null, true);
        }
    }

    public record ArtifactHandle(Path path, String contentType, String fileName) {}

    private record ArtifactRegistration(String outputDirectory, String contentType) {}
}
