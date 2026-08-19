package org.gemo.apex.platform.skill;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.skill.SkillResource;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.runtime.skill.FileSkillProvider;
import org.junit.jupiter.api.Test;

class RuntimeSkillRegistryTest {
    @Test
    void lazilyLoadsFileSkillsAndRoutesResources() throws Exception {
        Path root = Files.createTempDirectory(Path.of("target"), "skills"),
                dir = Files.createDirectory(root.resolve("pdf"));
        Files.writeString(dir.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n使用说明");
        Path references = Files.createDirectory(dir.resolve("references"));
        Files.writeString(references.resolve("guide.txt"), "资源");
        var provider = new FileSkillProvider(root);
        var registry = new RuntimeSkillRegistry(List.of(provider));
        assertEquals(List.of("pdf"), registry.loadSkills().stream().map(SkillMeta::name).toList());

        Files.writeString(references.resolve("late.txt"), "发现后新增资源");
        Files.writeString(
                dir.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n更新后的使用说明");
        SkillDefinition loaded = registry.loadSkill("pdf");
        assertEquals("更新后的使用说明", loaded.instructions());
        assertSame(loaded, provider.loadSkill("pdf"));
        assertEquals(Set.of("references/guide.txt"), loaded.resources().keySet());
        SkillResource resource = loaded.resources().get("references/guide.txt");
        assertEquals("references/guide.txt", resource.path());
        assertEquals("guide.txt", resource.fileName());
        assertFalse(resource.fileType().isBlank());
        assertNull(resource.content());

        Files.writeString(dir.resolve("SKILL.md"), "---\nname: pdf\ndescription: PDF\n---\n不应覆盖缓存");
        assertEquals("更新后的使用说明", registry.loadSkill("pdf").instructions());
        assertEquals("资源", registry.loadResource("pdf", "references/guide.txt"));
        assertEquals("资源", resource.content());
        Files.writeString(references.resolve("guide.txt"), "不应覆盖资源缓存");
        assertEquals("资源", registry.loadResource("pdf/references/guide.txt"));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.loadResource("pdf", "../outside.txt"));
    }

    @Test
    void snapshotsProviderMetadataAndRoutesLazyLoadsToLastProvider() {
        var first = new CountingSkillProvider("pdf", "第一版");
        var second = new CountingSkillProvider("pdf", "第二版");

        var registry = new RuntimeSkillRegistry(List.of(first, second));

        assertEquals(1, first.metadataLoads.get());
        assertEquals(1, second.metadataLoads.get());
        assertEquals(0, first.skillLoads.get());
        assertEquals(0, second.skillLoads.get());
        assertEquals("第二版", registry.loadSkill("pdf").instructions());
        assertEquals(0, first.skillLoads.get());
        assertEquals(1, second.skillLoads.get());
        assertEquals("second.txt", registry.loadResource("pdf/second.txt"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeSkillRegistry(
                                List.of(new CountingSkillProvider("duplicate", "重复", true))));
    }

    @Test
    void rejectsInvalidProviderResults() {
        SkillProvider nullMetadata = provider(null, null);
        assertThrows(NullPointerException.class, () -> new RuntimeSkillRegistry(List.of(nullMetadata)));

        SkillProvider nullSkill = provider(List.of(new SkillMeta("pdf", "PDF")), null);
        RuntimeSkillRegistry nullSkillRegistry = new RuntimeSkillRegistry(List.of(nullSkill));
        assertThrows(NullPointerException.class, () -> nullSkillRegistry.loadSkill("pdf"));

        SkillDefinition wrongSkill =
                new SkillDefinition(new SkillMeta("word", "Word"), "使用 Word 指令");
        RuntimeSkillRegistry wrongSkillRegistry =
                new RuntimeSkillRegistry(
                        List.of(provider(List.of(new SkillMeta("pdf", "PDF")), wrongSkill)));
        assertThrows(IllegalStateException.class, () -> wrongSkillRegistry.loadSkill("pdf"));
    }

    private static SkillProvider provider(List<SkillMeta> metadata, SkillDefinition skill) {
        return new SkillProvider() {
            @Override
            public List<SkillMeta> loadSkills() {
                return metadata;
            }

            @Override
            public SkillDefinition loadSkill(String skillName) {
                return skill;
            }

            @Override
            public String loadResource(String skillName, String resourcePath) {
                return resourcePath;
            }

            @Override
            public String loadResource(String path) {
                return path;
            }
        };
    }

    private static final class CountingSkillProvider implements SkillProvider {
        private final SkillMeta meta;
        private final String instructions;
        private final boolean duplicateMetadata;
        private final AtomicInteger metadataLoads = new AtomicInteger();
        private final AtomicInteger skillLoads = new AtomicInteger();

        private CountingSkillProvider(String name, String instructions) {
            this(name, instructions, false);
        }

        private CountingSkillProvider(String name, String instructions, boolean duplicateMetadata) {
            meta = new SkillMeta(name, instructions);
            this.instructions = instructions;
            this.duplicateMetadata = duplicateMetadata;
        }

        @Override
        public List<SkillMeta> loadSkills() {
            metadataLoads.incrementAndGet();
            return duplicateMetadata ? List.of(meta, meta) : List.of(meta);
        }

        @Override
        public SkillDefinition loadSkill(String skillName) {
            skillLoads.incrementAndGet();
            return new SkillDefinition(meta, instructions);
        }

        @Override
        public String loadResource(String skillName, String resourcePath) {
            return resourcePath;
        }

        @Override
        public String loadResource(String path) {
            return path;
        }
    }
}
