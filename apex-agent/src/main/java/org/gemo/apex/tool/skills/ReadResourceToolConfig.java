package org.gemo.apex.tool.skills;

import java.util.List;
import java.util.function.Function;

public final class ReadResourceToolConfig {

    static final String DEFAULT_NAME = "read_skill_resource";
    static final String DEFAULT_DESCRIPTION = "Returns the content of a resource referenced in the skill";
    static final String DEFAULT_SKILL_NAME_PARAMETER_NAME = "skill_name";
    static final String DEFAULT_SKILL_NAME_PARAMETER_DESCRIPTION = "The name of the skill for which the resource should be read";
    static final String DEFAULT_RELATIVE_PATH_PARAMETER_NAME = "relative_path";
    static final Function<List<? extends Skill>, String> DEFAULT_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER =
            skills -> "Relative path to the resource. For example: " + skills.stream()
                    .flatMap(skill -> skill.resources().stream())
                    .findFirst()
                    .map(SkillResource::relativePath)
                    .orElse("references/example.txt");

    final String name;
    final String description;
    final String skillNameParameterName;
    final String skillNameParameterDescription;
    final String relativePathParameterName;
    final String relativePathParameterDescription;
    final Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider;
    final boolean returnDirect;

    private ReadResourceToolConfig(Builder builder) {
        this.name = builder.name == null ? DEFAULT_NAME : builder.name;
        this.description = builder.description == null ? DEFAULT_DESCRIPTION : builder.description;
        this.skillNameParameterName = builder.skillNameParameterName == null ? DEFAULT_SKILL_NAME_PARAMETER_NAME
                : builder.skillNameParameterName;
        this.skillNameParameterDescription = builder.skillNameParameterDescription == null
                ? DEFAULT_SKILL_NAME_PARAMETER_DESCRIPTION : builder.skillNameParameterDescription;
        this.relativePathParameterName = builder.relativePathParameterName == null
                ? DEFAULT_RELATIVE_PATH_PARAMETER_NAME : builder.relativePathParameterName;
        this.relativePathParameterDescription = builder.relativePathParameterDescription;
        this.relativePathParameterDescriptionProvider = builder.relativePathParameterDescriptionProvider == null
                ? DEFAULT_RELATIVE_PATH_PARAMETER_DESCRIPTION_PROVIDER
                : builder.relativePathParameterDescriptionProvider;
        this.returnDirect = builder.returnDirect != null && builder.returnDirect;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String name;
        private String description;
        private String skillNameParameterName;
        private String skillNameParameterDescription;
        private String relativePathParameterName;
        private String relativePathParameterDescription;
        private Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider;
        private Boolean returnDirect;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder skillNameParameterName(String skillNameParameterName) {
            this.skillNameParameterName = skillNameParameterName;
            return this;
        }

        public Builder skillNameParameterDescription(String skillNameParameterDescription) {
            this.skillNameParameterDescription = skillNameParameterDescription;
            return this;
        }

        public Builder relativePathParameterName(String relativePathParameterName) {
            this.relativePathParameterName = relativePathParameterName;
            return this;
        }

        public Builder relativePathParameterDescription(String relativePathParameterDescription) {
            this.relativePathParameterDescription = relativePathParameterDescription;
            return this;
        }

        public Builder relativePathParameterDescriptionProvider(
                Function<List<? extends Skill>, String> relativePathParameterDescriptionProvider) {
            this.relativePathParameterDescriptionProvider = relativePathParameterDescriptionProvider;
            return this;
        }

        public Builder returnDirect(Boolean returnDirect) {
            this.returnDirect = returnDirect;
            return this;
        }

        public ReadResourceToolConfig build() {
            return new ReadResourceToolConfig(this);
        }
    }
}
