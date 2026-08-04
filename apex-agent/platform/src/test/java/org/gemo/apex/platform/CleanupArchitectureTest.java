package org.gemo.apex.platform;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CleanupArchitectureTest {
    private static final List<String> TARGET_MODULES = List.of(
            "protocol", "common", "core-extension", "core", "kit", "runtime", "platform", "memory");

    @Test
    void 父Reactor只保留八个目标模块() throws Exception {
        Path reactorRoot = Path.of("..").toAbsolutePath().normalize();
        Element project = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(reactorRoot.resolve("pom.xml").toFile()).getDocumentElement();
        Element modules = (Element) project.getElementsByTagName("modules").item(0);
        var entries = modules.getElementsByTagName("module");
        List<String> actual = new ArrayList<>();
        for (int index = 0; index < entries.getLength(); index++) {
            actual.add(entries.item(index).getTextContent().trim());
        }

        assertEquals(TARGET_MODULES, actual);
        assertFalse(Files.exists(reactorRoot.resolve("legacy")));
        assertFalse(Files.exists(reactorRoot.resolve("architecture-tests")));
    }
}
