package org.gemo.apex.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gemo.apex.common.skill.SkillDefinition;
import org.gemo.apex.common.skill.SkillMeta;
import org.gemo.apex.common.tool.ToolCall;
import org.gemo.apex.extension.skill.SkillProvider;
import org.gemo.apex.kit.tool.ReadSkillResourceTool;
import org.junit.jupiter.api.Test;

class ReadSkillResourceToolTest {
    private static final SkillProvider SKILLS =
            new SkillProvider() {
                @Override
                public List<SkillMeta> loadSkills() {
                    return List.of();
                }

                @Override
                public SkillDefinition loadSkill(String skillName) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public String loadResource(String skillName, String resourcePath) {
                    return skillName + ":" + resourcePath;
                }

                @Override
                public String loadResource(String path) {
                    throw new UnsupportedOperationException();
                }
            };

    @Test
    void readsResourcesOnlyForEnabledSkills() {
        ToolCall call =
                KitFixtures.call(
                        ReadSkillResourceTool.NAME,
                        Map.of("skillName", "pdf", "path", "references/guide.txt"));

        assertEquals(
                "pdf:references/guide.txt",
                new ReadSkillResourceTool(SKILLS, Set.of("pdf"))
                        .execute(call, KitFixtures.execution(1, 1), KitFixtures.OBSERVER)
                        .content());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ReadSkillResourceTool(SKILLS, Set.of())
                                .execute(call, KitFixtures.execution(1, 1), KitFixtures.OBSERVER));
    }
}
