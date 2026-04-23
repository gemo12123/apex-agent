package org.gemo.apex.tool.skills;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

abstract class AbstractSkill implements Skill {

    private final String name;
    private final String description;
    private final String content;
    private final List<SkillResource> resources;

    protected AbstractSkill(BaseBuilder<?> builder) {
        this.name = Validators.notBlank(builder.name, "name");
        this.description = Validators.notBlank(builder.description, "description");
        this.content = Validators.notBlank(builder.content, "content");
        this.resources = List.copyOf(builder.resources == null ? List.of() : builder.resources);
        validateUniquePaths(this.resources);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String content() {
        return content;
    }

    @Override
    public List<SkillResource> resources() {
        return resources;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractSkill that)) {
            return false;
        }
        return Objects.equals(name, that.name)
                && Objects.equals(description, that.description)
                && Objects.equals(content, that.content)
                && Objects.equals(resources, that.resources);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, content, resources);
    }

    private static void validateUniquePaths(List<SkillResource> resources) {
        Set<String> seenPaths = new HashSet<>();
        for (SkillResource resource : resources) {
            if (!seenPaths.add(resource.relativePath())) {
                throw new IllegalStateException("Duplicate skill resource path detected: '" + resource.relativePath()
                        + "'");
            }
        }
    }

    abstract static class BaseBuilder<B extends BaseBuilder<B>> {

        private String name;
        private String description;
        private String content;
        private Collection<? extends SkillResource> resources;

        @SuppressWarnings("unchecked")
        public B name(String name) {
            this.name = name;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B description(String description) {
            this.description = description;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B content(String content) {
            this.content = content;
            return (B) this;
        }

        @SuppressWarnings("unchecked")
        public B resources(Collection<? extends SkillResource> resources) {
            this.resources = resources;
            return (B) this;
        }
    }
}
