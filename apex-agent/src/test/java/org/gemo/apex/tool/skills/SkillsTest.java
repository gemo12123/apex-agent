package org.gemo.apex.tool.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillsTest {

    @TempDir
    Path tempDir;

    @Test
    void buildShouldUseLegacyActivateToolContractAndNewPromptFormat() {
        Skills skills = Skills.from(Skill.builder()
                .name("meeting-skill")
                .description("Meeting workflow")
                .content("Follow the meeting workflow")
                .build());

        ToolCallback activateSkill = skills.toolCallbacks()[0];

        assertEquals("activate_skill", activateSkill.getToolDefinition().name());
        assertEquals(
                "通过名称激活一个专用智能体技能。可用技能名称：meeting-skill。返回被包裹在 <activated_skill> 标签中的该技能指令。这些指令为当前任务提供专门的指导。当你识别到某个任务与某个技能的描述相匹配时，应使用该技能。只能使用 <available_skills> 部分中完全一致的技能名称。",
                activateSkill.getToolDefinition().description());
        assertTrue(skills.formatAvailableSkills().contains("<available_skills>"));
        assertTrue(skills.formatAvailableSkills().contains("<name>meeting-skill</name>"));
        assertTrue(skills.formatAvailableSkills().contains("<description>Meeting workflow</description>"));
        assertEquals("""
                <activated_skill name="meeting-skill">
                  <instructions>
                    Follow the meeting workflow
                  </instructions>
                </activated_skill>
                """, activateSkill.call("{\"command\":\"meeting-skill\"}"));
    }

    @Test
    void loadSkillsShouldOnlyReadDirectChildDirectories() throws IOException {
        writeSkill(tempDir.resolve("direct-skill"), "direct-skill", "Direct description", "Direct instructions");
        writeSkill(tempDir.resolve("group").resolve("nested-skill"), "nested-skill", "Nested description",
                "Nested instructions");

        assertEquals(1, FileSystemSkillLoader.loadSkills(tempDir).size());
        assertEquals("direct-skill", FileSystemSkillLoader.loadSkills(tempDir).getFirst().name());
    }

    private void writeSkill(Path skillDirectory, String name, String description, String content) throws IOException {
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), """
                ---
                name: %s
                description: %s
                ---

                %s
                """.formatted(name, description, content));
    }
}
