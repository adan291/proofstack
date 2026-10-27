package dev.proofstack.core.domain.content;

import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** What a piece of content is about: a project or an evidence of the profile. */
public sealed interface ContentSubject permits ContentSubject.ProjectSubject, ContentSubject.EvidenceSubject {

    String id();

    String name();

    /** All factual text of the subject, used for grounding checks. */
    String sourceText();

    record ProjectSubject(Project project) implements ContentSubject {
        public ProjectSubject {
            Objects.requireNonNull(project, "project");
        }

        @Override
        public String id() {
            return project.id();
        }

        @Override
        public String name() {
            return project.name();
        }

        @Override
        public String sourceText() {
            List<String> parts = new ArrayList<>();
            parts.add(project.name());
            parts.add(project.summary());
            Stream.of(project.problem(), project.solution()).filter(Objects::nonNull).forEach(parts::add);
            parts.addAll(project.techStack());
            parts.addAll(project.learnings());
            return String.join("\n", parts);
        }
    }

    record EvidenceSubject(Evidence evidence) implements ContentSubject {
        public EvidenceSubject {
            Objects.requireNonNull(evidence, "evidence");
        }

        @Override
        public String id() {
            return evidence.id();
        }

        @Override
        public String name() {
            return evidence.title();
        }

        @Override
        public String sourceText() {
            return String.join("\n", evidence.title(), evidence.context(), evidence.action(), evidence.result());
        }
    }

    /** Finds a project or an evidence by id (projects take precedence). */
    static Optional<ContentSubject> resolve(Profile profile, String id) {
        return profile.project(id).<ContentSubject>map(ProjectSubject::new)
                .or(() -> profile.evidence(id).map(EvidenceSubject::new));
    }

    /** Ids that can be used as subject, for helpful error messages. */
    static List<String> availableIds(Profile profile) {
        return Stream.concat(profile.projects().stream().map(Project::id),
                profile.evidence().stream().map(Evidence::id)).toList();
    }
}
