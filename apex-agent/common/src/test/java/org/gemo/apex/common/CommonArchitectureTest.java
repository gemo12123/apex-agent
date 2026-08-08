package org.gemo.apex.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonArchitectureTest {
    private static final List<String> FORBIDDEN = List.of(
            "org.springframework", "jakarta.servlet", "javax.servlet",
            "jakarta.persistence", "javax.persistence", "org.apache.ibatis",
            "com.baomidou", "java.sql", "com.alibaba.fastjson", "com.alibaba.fastjson2");

    /**
     * common标准源码不得引入框架数据库或Fastjson类型
     */
    @Test
    void commonProductionSourceExcludesFrameworkDatabaseAndFastjsonTypes() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        try (var files = Files.walk(sourceRoot)) {
            List<Path> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .filter(this::containsForbiddenImport)
                    .toList();
            assertTrue(violations.isEmpty(), "common 存在禁止依赖: " + violations);
        }
    }

    private boolean containsForbiddenImport(Path path) {
        try {
            String source = Files.readString(path);
            return source.lines().filter(line -> line.startsWith("import "))
                    .anyMatch(line -> FORBIDDEN.stream().anyMatch(line::contains));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
