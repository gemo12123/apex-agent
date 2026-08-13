package org.gemo.apex.runtime.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.junit.jupiter.api.Test;

class FileSkillProviderTest {
    @Test
    void discoversMetadataAndCachesContentFromPackagedClasspathSkill() throws Exception {
        Path jar = Files.createTempFile(Path.of("target"), "skill-provider", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            add(output, "jar-skills/");
            add(output, "jar-skills/pdf/");
            add(
                    output,
                    "jar-skills/pdf/SKILL.md",
                    "---\nname: pdf\ndescription: PDF\n---\nJAR 使用说明");
            add(output, "jar-skills/pdf/references/");
            add(output, "jar-skills/pdf/references/guide.md", "JAR 资源");
        }

        try (URLClassLoader loader =
                new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, null)) {
            FileSkillProvider provider = new FileSkillProvider("classpath:jar-skills", loader);

            assertEquals(
                    List.of("pdf"), provider.loadSkills().stream().map(SkillMeta::name).toList());
            SkillDefinition definition = provider.loadSkill("pdf");
            assertEquals("JAR 使用说明", definition.instructions());
            assertEquals(Set.of("references/guide.md"), definition.resources().keySet());
            assertNull(definition.resources().get("references/guide.md").content());

            assertEquals("JAR 资源", provider.loadResource("pdf/references/guide.md"));
            assertEquals("JAR 资源", definition.resources().get("references/guide.md").content());
            assertSame(definition, provider.loadSkill("pdf"));
        }
    }

    private void add(JarOutputStream output, String name) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.closeEntry();
    }

    private void add(JarOutputStream output, String name, String content) throws Exception {
        output.putNextEntry(new JarEntry(name));
        output.write(content.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
