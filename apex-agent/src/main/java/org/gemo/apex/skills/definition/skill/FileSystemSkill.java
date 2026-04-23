package org.gemo.apex.skills.definition.skill;

import org.gemo.apex.skills.definition.skill.impl.DefaultFileSystemSkill;

import java.nio.file.Path;

public interface FileSystemSkill extends Skill {

    Path basePath();

    static DefaultFileSystemSkill.Builder builder() {
        return new DefaultFileSystemSkill.Builder();
    }
}
