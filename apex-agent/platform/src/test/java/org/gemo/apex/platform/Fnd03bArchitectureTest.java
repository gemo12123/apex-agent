package org.gemo.apex.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class Fnd03bArchitectureTest {
    private static final Path REACTOR_ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final List<String> TARGET_MODULES =
            List.of(
                    "protocol",
                    "common",
                    "core-extension",
                    "core",
                    "kit",
                    "runtime",
                    "platform",
                    "memory");
    private static final Map<String, Set<String>> PROJECT_DEPENDENCIES =
            Map.of(
                    "protocol", Set.of(),
                    "common", Set.of("protocol"),
                    "core-extension", Set.of("protocol", "common"),
                    "core", Set.of("protocol", "common", "core-extension"),
                    "kit", Set.of("protocol", "common", "core-extension"),
                    "runtime", Set.of("protocol", "common", "core-extension", "core", "kit"),
                    "platform", Set.of("protocol", "common", "core-extension", "runtime"),
                    "memory", Set.of());
    private static final Map<String, String> PACKAGE_ROOTS =
            Map.of(
                    "protocol", "org.gemo.apex.protocol",
                    "common", "org.gemo.apex.common",
                    "core-extension", "org.gemo.apex.extension",
                    "core", "org.gemo.apex.core",
                    "kit", "org.gemo.apex.kit",
                    "runtime", "org.gemo.apex.runtime",
                    "platform", "org.gemo.apex.platform");
    private static final List<String> REMOVED_TYPES =
            List.of(
                    "SuperAgent",
                    "PlanExecutor",
                    "StageToolResolver",
                    "WritePlanTool",
                    "UpdatePlanTool",
                    "DefaultAgentLifecycleHookRuntime",
                    "AgentHookRuntime",
                    "ModeEnum",
                    "executionMode");

    /** 父Reactor与项目依赖精确符合八模块最终图 */
    @Test
    void parentReactorAndProjectDependenciesExactlyMatchFinalEightModuleGraph() throws Exception {
        assertEquals(TARGET_MODULES, modules(parsePom(REACTOR_ROOT.resolve("pom.xml"))));
        assertFalse(Files.exists(REACTOR_ROOT.resolve("legacy")));
        assertFalse(Files.exists(REACTOR_ROOT.resolve("architecture-tests")));

        for (String module : TARGET_MODULES) {
            assertEquals(
                    PROJECT_DEPENDENCIES.get(module),
                    projectDependencies(module),
                    module + " 项目依赖越界");
        }
    }

    /** 七个代码模块遵守包根和技术栈边界 */
    @Test
    void sevenCodeModulesRespectPackageRootAndTechnologyBoundaries() throws IOException {
        Map<String, List<String>> forbidden = new LinkedHashMap<>();
        forbidden.put(
                "protocol",
                List.of(
                        "org.gemo.apex.common",
                        "org.gemo.apex.extension",
                        "org.gemo.apex.core",
                        "org.gemo.apex.runtime",
                        "org.gemo.apex.platform",
                        "org.springframework.ai",
                        "jakarta.servlet",
                        "javax.servlet",
                        "org.springframework.data",
                        "org.apache.ibatis",
                        "jakarta.persistence"));
        forbidden.put(
                "common",
                List.of(
                        "org.gemo.apex.extension",
                        "org.gemo.apex.core",
                        "org.gemo.apex.runtime",
                        "org.gemo.apex.platform",
                        "org.springframework",
                        "jakarta.servlet",
                        "javax.servlet",
                        "org.apache.ibatis",
                        "com.baomidou",
                        "jakarta.persistence",
                        "javax.persistence",
                        "java.sql"));
        forbidden.put(
                "core-extension",
                List.of(
                        "org.gemo.apex.core",
                        "org.gemo.apex.runtime",
                        "org.gemo.apex.platform",
                        "org.springframework",
                        "jakarta.servlet",
                        "javax.servlet"));
        forbidden.put(
                "core",
                List.of(
                        "org.gemo.apex.runtime",
                        "org.gemo.apex.platform",
                        "org.gemo.apex.memory",
                        "org.springframework",
                        "jakarta.servlet",
                        "javax.servlet",
                        "java.sql",
                        "org.apache.ibatis",
                        "com.baomidou",
                        "io.modelcontextprotocol"));
        forbidden.put(
                "kit",
                List.of(
                        "org.gemo.apex.core",
                        "org.gemo.apex.runtime",
                        "org.gemo.apex.platform",
                        "org.gemo.apex.memory",
                        "org.springframework"));
        forbidden.put(
                "runtime",
                List.of(
                        "org.gemo.apex.platform",
                        "org.gemo.apex.memory",
                        "org.springframework.context",
                        "org.springframework.beans",
                        "org.springframework.stereotype",
                        "org.springframework.boot",
                        "jakarta.servlet",
                        "javax.servlet"));
        forbidden.put("platform", List.of("org.gemo.apex.core", "org.gemo.apex.memory"));

        for (String module : PACKAGE_ROOTS.keySet()) {
            for (Path source : javaSources(module)) {
                String content = Files.readString(source);
                assertTrue(
                        content.contains("package " + PACKAGE_ROOTS.get(module) + ";")
                                || content.contains("package " + PACKAGE_ROOTS.get(module) + "."),
                        source + " 不在模块包根 " + PACKAGE_ROOTS.get(module));
                if (source.toString().contains("src" + File.separator + "main")) {
                    requireNoForbidden(source.toString(), content, forbidden.get(module));
                    requireNoForbidden(
                            source.toString(),
                            content,
                            List.of(
                                    "com.alibaba.fastjson",
                                    "com.alibaba.fastjson2",
                                    "org.gemo.apex.legacy"));
                } else {
                    requireNoForbiddenImport(
                            source.toString(),
                            content,
                            List.of(
                                    "com.alibaba.fastjson",
                                    "com.alibaba.fastjson2",
                                    "org.gemo.apex.legacy"));
                }
            }
        }
    }

    /** 产品入口迁移豁免与构造语义均已收口 */
    @Test
    void productEntryMigrationExemptionsAndConstructionSemanticsAreConsolidated()
            throws IOException {
        List<Path> productionSources = allProductionSources();
        List<Path> bootEntries = sourcesContaining(productionSources, "@SpringBootApplication");
        assertEquals(
                List.of(
                        REACTOR_ROOT.resolve(
                                "platform/src/main/java/org/gemo/apex/platform/bootstrap/ApexApplication.java")),
                bootEntries);

        List<String> repackageOwners = new ArrayList<>();
        for (String module : TARGET_MODULES) {
            String pom = Files.readString(REACTOR_ROOT.resolve(module).resolve("pom.xml"));
            if (pom.contains("<goal>repackage</goal>")) {
                repackageOwners.add(module);
            }
        }
        assertEquals(List.of("platform"), repackageOwners);
        assertEquals(
                "exemptions: []",
                Files.readString(REACTOR_ROOT.resolve("migration-exemptions.yml")).trim());

        for (Path source : allProductionSources()) {
            String content = Files.readString(source);
            requireNoForbidden(source.toString(), content, REMOVED_TYPES);
        }

        List<Path> agentBuildDispatchers =
                sourcesContaining(productionSources, "resolve(HookPoint.AGENT_BUILD");
        assertEquals(
                List.of(
                        REACTOR_ROOT.resolve(
                                "core/src/main/java/org/gemo/apex/core/definition/AgentDefinitionAssembler.java")),
                agentBuildDispatchers);
    }

    /** memory保持非编译归档且不进入其他模块产物 */
    @Test
    void memoryRemainsNonCompiledArchiveAndIsExcludedFromOtherModuleArtifacts() throws Exception {
        Path memory = REACTOR_ROOT.resolve("memory");
        Element pom = parsePom(memory.resolve("pom.xml"));
        assertEquals("pom", directText(pom, "packaging"));
        assertEquals(0, pom.getElementsByTagName("dependencies").getLength());
        assertEquals(0, pom.getElementsByTagName("build").getLength());
        assertFalse(Files.exists(memory.resolve("src/main")));
        assertFalse(Files.exists(memory.resolve("src/test")));
        assertTrue(Files.isRegularFile(memory.resolve("archive/MANIFEST.md")));

        for (String module : TARGET_MODULES) {
            Path classes = REACTOR_ROOT.resolve(module).resolve("target/classes");
            if (!Files.exists(classes)) {
                continue;
            }
            try (var files = Files.walk(classes)) {
                assertTrue(
                        files.noneMatch(
                                path ->
                                        path.getFileName()
                                                        .toString()
                                                        .equals("MemoryLifecycleManager.class")
                                                || path.getFileName()
                                                        .toString()
                                                        .equals("SessionSearchTool.class")
                                                || path.getFileName()
                                                        .toString()
                                                        .equals(
                                                                "SkillExperienceLearningConfiguration.class")),
                        module + " 打包了 memory 归档类型");
            }
        }
    }

    /** 典型非法源码会被统一禁止规则拒绝 */
    @Test
    void unifiedProhibitionRuleRejectsTypicalIllegalSource() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        requireNoForbidden(
                                "fixture.java",
                                "import org.springframework.context.ApplicationContext;",
                                List.of("org.springframework")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        requireNoForbidden(
                                "fixture.java",
                                "import com.alibaba.fastjson2.JSON;",
                                List.of("com.alibaba.fastjson2")));
    }

    private Set<String> projectDependencies(String module) throws Exception {
        Element pom = parsePom(REACTOR_ROOT.resolve(module).resolve("pom.xml"));
        NodeList dependencies = pom.getElementsByTagName("dependency");
        Set<String> actual = new HashSet<>();
        for (int index = 0; index < dependencies.getLength(); index++) {
            Element dependency = (Element) dependencies.item(index);
            if (!"org.gemo.apex".equals(directText(dependency, "groupId"))) {
                continue;
            }
            String artifactId = directText(dependency, "artifactId");
            if (artifactId.startsWith("apex-agent-")) {
                actual.add(artifactId.substring("apex-agent-".length()));
            }
        }
        return actual;
    }

    private List<Path> javaSources(String module) throws IOException {
        return javaFiles(REACTOR_ROOT.resolve(module), path -> path.toString().contains("src"));
    }

    private List<Path> allProductionSources() throws IOException {
        List<Path> result = new ArrayList<>();
        for (String module : PACKAGE_ROOTS.keySet()) {
            result.addAll(
                    javaFiles(REACTOR_ROOT.resolve(module).resolve("src/main/java"), path -> true));
        }
        return result;
    }

    private List<Path> javaFiles(Path root, Predicate<Path> filter) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (var files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(filter)
                    .sorted()
                    .toList();
        }
    }

    private List<Path> sourcesContaining(List<Path> sources, String marker) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path source : sources) {
            if (Files.readString(source).contains(marker)) {
                result.add(source);
            }
        }
        return result;
    }

    private void requireNoForbidden(String owner, String content, List<String> forbidden) {
        for (String token : forbidden) {
            if (content.contains(token)) {
                throw new IllegalStateException(owner + " 包含禁止内容: " + token);
            }
        }
    }

    private void requireNoForbiddenImport(String owner, String content, List<String> forbidden) {
        for (String line : content.lines().filter(value -> value.startsWith("import ")).toList()) {
            requireNoForbidden(owner, line, forbidden);
        }
    }

    private Element parsePom(Path path) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(path.toFile())
                .getDocumentElement();
    }

    private List<String> modules(Element project) {
        Element modules = (Element) project.getElementsByTagName("modules").item(0);
        NodeList entries = modules.getElementsByTagName("module");
        List<String> actual = new ArrayList<>();
        for (int index = 0; index < entries.getLength(); index++) {
            actual.add(entries.item(index).getTextContent().trim());
        }
        return actual;
    }

    private String directText(Element parent, String tagName) {
        NodeList children = parent.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            if (children.item(index) instanceof Element child
                    && tagName.equals(child.getTagName())) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }
}
