package org.gemo.apex.architecture;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yaml.snakeyaml.Yaml;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationArchitectureTest {

    private static final List<String> TARGET_MODULES = List.of(
            "protocol", "common", "core-extension", "core", "kit", "runtime", "platform", "memory");

    private static final Map<String, Set<String>> EXPECTED_DEPENDENCIES = Map.of(
            "protocol", Set.of(),
            "common", Set.of("protocol"),
            "core-extension", Set.of("protocol", "common"),
            "core", Set.of("protocol", "common", "core-extension"),
            "kit", Set.of("protocol", "common", "core-extension"),
            "runtime", Set.of("protocol", "common", "core-extension", "core", "kit"),
            "platform", Set.of("protocol", "common", "core-extension", "runtime"),
            "memory", Set.of());

    private final Path reactorRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void targetModulesDeclareExactMigrationDependenciesWithoutLegacy() throws Exception {
        Map<String, Set<String>> graph = loadProjectGraph();

        assertEquals(EXPECTED_DEPENDENCIES, graph);
        assertTrue(graph.values().stream().noneMatch(dependencies -> dependencies.contains("legacy")));
        assertAcyclic(graph);
    }

    @Test
    void invalidReverseDependencyAndCycleAreRejected() {
        Map<String, Set<String>> reverseDependency = new LinkedHashMap<>(EXPECTED_DEPENDENCIES);
        reverseDependency.put("protocol", Set.of("legacy"));
        assertFalse(reverseDependency.values().stream().noneMatch(dependencies -> dependencies.contains("legacy")));

        Map<String, Set<String>> cyclic = new LinkedHashMap<>(EXPECTED_DEPENDENCIES);
        cyclic.put("protocol", Set.of("common"));
        assertThrows(IllegalStateException.class, () -> assertAcyclic(cyclic));
    }

    @Test
    void migrationExemptionsAreSpecificCompleteAndNotExpiredSilently() throws IOException {
        Path exemptionsFile = reactorRoot.resolve("migration-exemptions.yml");
        assertTrue(Files.isRegularFile(exemptionsFile), "迁移豁免文件缺失时必须失败关闭");

        List<Map<String, Object>> exemptions;
        try (InputStream input = Files.newInputStream(exemptionsFile)) {
            Object value = new Yaml().load(input);
            assertTrue(value instanceof Map<?, ?>);
            Object entries = ((Map<?, ?>) value).get("exemptions");
            assertTrue(entries instanceof List<?>);
            exemptions = ((List<?>) entries).stream()
                    .map(entry -> (Map<String, Object>) entry)
                    .toList();
        }

        assertFalse(exemptions.isEmpty());
        for (Map<String, Object> exemption : exemptions) {
            ArchitectureRuleDescriptor descriptor = new ArchitectureRuleDescriptor(
                    required(exemption, "rule"),
                    required(exemption, "owner"),
                    required(exemption, "reason"),
                    required(exemption, "expires-at-task"));
            assertFalse(descriptor.ruleId().isBlank());
            assertFalse(descriptor.ownerModule().isBlank());
            assertFalse(descriptor.rationale().isBlank());
            assertFalse(descriptor.expiryTask().isBlank());
        }

        Map<String, Object> convergence = exemptions.stream()
                .filter(entry -> "spring-ai-dependency-convergence".equals(entry.get("rule")))
                .findFirst()
                .orElseThrow();
        assertEquals("RUN-02", convergence.get("owner"));
        assertEquals("RUN-02", convergence.get("expires-at-task"));
        assertTrue(convergence.get("coordinates") instanceof List<?>);
        List<?> coordinates = (List<?>) convergence.get("coordinates");
        assertFalse(coordinates.isEmpty());
        assertTrue(coordinates.stream().allMatch(value -> {
            String coordinate = String.valueOf(value);
            return coordinate.matches("[^:*]+:[^:*]+") && !coordinate.contains("*");
        }));
        Path evidence = reactorRoot.resolve(required(convergence, "baseline-evidence")).normalize();
        assertTrue(Files.isRegularFile(evidence), "Spring AI convergence 豁免必须回指 FND-01 原始证据");
    }

    private Map<String, Set<String>> loadProjectGraph() throws Exception {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (String module : TARGET_MODULES) {
            Path pom = reactorRoot.resolve(module).resolve("pom.xml");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom.toFile());
            Set<String> dependencies = new HashSet<>();
            NodeList nodes = document.getElementsByTagName("dependency");
            for (int index = 0; index < nodes.getLength(); index++) {
                Element dependency = (Element) nodes.item(index);
                String groupId = childText(dependency, "groupId");
                String artifactId = childText(dependency, "artifactId");
                String scope = childText(dependency, "scope");
                assertFalse("system".equals(scope), module + " 不得声明 system scope 依赖");
                if ("org.gemo.apex".equals(groupId) && artifactId.startsWith("apex-agent-")) {
                    dependencies.add(artifactId.substring("apex-agent-".length()));
                }
            }
            graph.put(module, Set.copyOf(dependencies));
        }
        return graph;
    }

    private void assertAcyclic(Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> active = new HashSet<>();
        ArrayDeque<String> path = new ArrayDeque<>();
        for (String module : graph.keySet()) {
            visit(module, graph, visited, active, path);
        }
    }

    private void visit(String module, Map<String, Set<String>> graph, Set<String> visited,
            Set<String> active, ArrayDeque<String> path) {
        if (active.contains(module)) {
            throw new IllegalStateException("检测到 Maven 模块依赖环：" + path + " -> " + module);
        }
        if (!visited.add(module)) {
            return;
        }
        active.add(module);
        path.addLast(module);
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            if (graph.containsKey(dependency)) {
                visit(dependency, graph, visited, active, path);
            }
        }
        path.removeLast();
        active.remove(module);
    }

    private String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private String required(Map<String, Object> entry, String key) {
        Object value = entry.get(key);
        assertTrue(value instanceof String && !((String) value).isBlank(), "豁免缺少必填字段：" + key);
        return (String) value;
    }
}
