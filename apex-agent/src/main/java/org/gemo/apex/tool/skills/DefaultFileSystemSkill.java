package org.gemo.apex.tool.skills;

import java.nio.file.Path;
import java.util.Objects;

public final class DefaultFileSystemSkill extends AbstractSkill implements FileSystemSkill {

    private final Path basePath;

    private DefaultFileSystemSkill(Builder builder) {
        super(builder);
        this.basePath = Validators.notNull(builder.basePath, "basePath");
    }

    @Override
    public Path basePath() {
        return basePath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultFileSystemSkill that)) {
            return false;
        }
        return super.equals(o) && Objects.equals(basePath, that.basePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), basePath);
    }

    public static final class Builder extends BaseBuilder<Builder> {

        private Path basePath;

        public Builder basePath(Path basePath) {
            this.basePath = basePath;
            return this;
        }

        public DefaultFileSystemSkill build() {
            return new DefaultFileSystemSkill(this);
        }
    }
}
