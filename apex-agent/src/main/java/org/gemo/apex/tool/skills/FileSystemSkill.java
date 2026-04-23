package org.gemo.apex.tool.skills;

import java.nio.file.Path;

public interface FileSystemSkill extends Skill {

    Path basePath();

    static DefaultFileSystemSkill.Builder builder() {
        return new DefaultFileSystemSkill.Builder();
    }
}
