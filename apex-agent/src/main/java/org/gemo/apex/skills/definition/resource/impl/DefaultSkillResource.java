package org.gemo.apex.skills.definition.resource.impl;

import org.gemo.apex.skills.definition.resource.SkillResource;
import org.gemo.apex.skills.support.Validators;

import java.util.Objects;

public final class DefaultSkillResource implements SkillResource {

    private final String relativePath;
    private final String content;

    private DefaultSkillResource(Builder builder) {
        this.relativePath = Validators.notBlank(builder.relativePath, "relativePath");
        this.content = Validators.notBlank(builder.content, "content");
    }

    @Override
    public String relativePath() {
        return relativePath;
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DefaultSkillResource that)) {
            return false;
        }
        return Objects.equals(relativePath, that.relativePath) && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, content);
    }

    public static final class Builder {

        private String relativePath;
        private String content;

        public Builder relativePath(String relativePath) {
            this.relativePath = relativePath;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public DefaultSkillResource build() {
            return new DefaultSkillResource(this);
        }
    }
}
