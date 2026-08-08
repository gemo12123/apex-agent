package org.gemo.apex.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoreArchitectureTest {
    /**
     * core源码不依赖SpringServletSse数据库或Mcp客户端
     */
    @Test
    void coreProductionSourceDoesNotDependOnSpringServletSseDatabaseOrMcpClient() throws IOException {
        String source = readJava(Path.of("src/main/java"));
        assertFalse(source.contains("org.springframework"));
        assertFalse(source.contains("SseEmitter"));
        assertFalse(source.contains("jakarta.servlet"));
        assertFalse(source.contains("javax.servlet"));
        assertFalse(source.contains("springframework.ai"));
        assertFalse(source.contains("McpClient"));
        assertFalse(source.contains("java.sql"));
    }

    /**
     * core只有一处业务迭代循环且固定文案只归ToolResultFactory所有
     */
    @Test
    void coreHasSingleBusinessIterationLoopAndReservesFixedTextForToolResultFactory() throws IOException {
        String source = readJava(Path.of("src/main/java"));
        assertEquals(1, occurrences(source, "for (int iterationNo = firstIteration"));
        assertFalse(source.contains("PlanExecutor"));
        assertFalse(source.contains("StageToolResolver"));
        assertEquals(1, occurrences(source, "用户拒绝执行"));
        assertEquals(1, occurrences(source, "达到最大轮次，强制结束"));
        assertEquals(1, occurrences(source, "请求已取消，工具未执行完成"));
    }

    private String readJava(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                result.append(Files.readString(file));
            }
        }
        return result.toString();
    }

    private int occurrences(String source, String target) {
        int count = 0, from = 0;
        while ((from = source.indexOf(target, from)) >= 0) { count++; from += target.length(); }
        return count;
    }
}
