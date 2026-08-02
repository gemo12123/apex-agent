package org.gemo.apex.kit;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class KitArchitectureTest {
    private static final Set<String> PROJECT_DEPENDENCIES = Set.of(
            "apex-agent-protocol", "apex-agent-common", "apex-agent-core-extension");
    private static final Set<String> FORBIDDEN_TYPE_NAMES = Set.of(
            "WritePlanTool", "UpdatePlanTool", "PlanExecutor", "StageTool", "StandardToolResultFactory");
    private static final Set<String> FORBIDDEN_REFERENCES = Set.of(
            "org/gemo/apex/core", "org/gemo/apex/runtime", "org/gemo/apex/platform",
            "org/springframework", "StandardToolResultFactory",
            "用户拒绝执行", "达到最大轮次，强制结束", "请求已取消，工具未执行完成");

    @Test
    void 项目直接依赖精确为三层公共契约() throws Exception {
        Element project = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(Path.of("pom.xml").toFile()).getDocumentElement();
        NodeList dependencies = project.getElementsByTagName("dependency");
        Set<String> actual = new HashSet<>();
        for (int i = 0; i < dependencies.getLength(); i++) {
            Element dependency = (Element) dependencies.item(i);
            if ("org.gemo.apex".equals(text(dependency, "groupId"))) {
                actual.add(text(dependency, "artifactId"));
            }
        }
        assertEquals(PROJECT_DEPENDENCIES, actual);
    }

    @Test
    void 编译产物不包含计划工具状态机Spring或core实现引用() throws Exception {
        Path classes = Path.of("target", "classes");
        assertTrue(Files.isDirectory(classes));
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String fileName = file.getFileName().toString();
                FORBIDDEN_TYPE_NAMES.forEach(name -> assertFalse(fileName.contains(name), file.toString()));
                String bytecode = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                FORBIDDEN_REFERENCES.forEach(reference -> assertFalse(bytecode.contains(reference),
                        file + " 包含禁止引用: " + reference));
            }
        }
    }

    private String text(Element parent, String tagName) {
        return parent.getElementsByTagName(tagName).item(0).getTextContent().trim();
    }
}
