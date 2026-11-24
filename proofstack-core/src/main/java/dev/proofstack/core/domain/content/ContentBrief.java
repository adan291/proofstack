package dev.proofstack.core.domain.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;

/**
 * Everything a generator may use. Anything not in the brief must not appear as a fact in the output.
 */
public record ContentBrief(
        ContentFormat format,
        String language,
        PersonalInfo author,
        ContentSubject subject,
        List<Evidence> relatedEvidence,
        List<Experience> relatedExperience) {

    public ContentBrief {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(subject, "subject");
        relatedEvidence = relatedEvidence == null ? List.of() : List.copyOf(relatedEvidence);
        relatedExperience = relatedExperience == null ? List.of() : List.copyOf(relatedExperience);
    }

    public ContentBrief(ContentFormat format, String language, PersonalInfo author, ContentSubject subject,
                        List<Evidence> relatedEvidence) {
        this(format, language, author, subject, relatedEvidence, List.of());
    }

    /**
     * Projects bring the evidence they reference; evidence brings the jobs that reference it, which is
     * where the facts usually are after a LinkedIn import (position descriptions).
     */
    public static ContentBrief of(Profile profile, ContentSubject subject, ContentFormat format, String language) {
        List<Evidence> related = switch (subject) {
            case ContentSubject.ProjectSubject p -> p.project().evidenceIds().stream()
                    .map(profile::evidence).flatMap(java.util.Optional::stream).toList();
            case ContentSubject.EvidenceSubject _ -> List.of();
        };
        List<Experience> jobs = switch (subject) {
            case ContentSubject.ProjectSubject _ -> List.of();
            case ContentSubject.EvidenceSubject e -> profile.experience().stream()
                    .filter(x -> x.evidenceIds().contains(e.id())).toList();
        };
        return new ContentBrief(format, language == null ? profile.language() : language,
                profile.person(), subject, related, jobs);
    }

    /** Factual source text: the base for the grounding check. */
    public String sourceText() {
        List<String> parts = new ArrayList<>();
        parts.add(author.name());
        parts.add(author.headline());
        if (author.summary() != null) {
            parts.add(author.summary());
        }
        parts.add(subject.sourceText());
        relatedEvidence.forEach(e -> parts.add(new ContentSubject.EvidenceSubject(e).sourceText()));
        relatedExperience.forEach(x -> {
            parts.add(x.role() + " " + x.company() + " " + x.period().start()
                    + x.period().endDate().map(end -> " " + end).orElse(""));
            parts.addAll(x.highlights());
            parts.addAll(x.technologies());
        });
        return String.join("\n", parts);
    }
}
