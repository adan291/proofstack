package dev.proofstack.core.application;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileMerge;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.port.in.ImportProfileUseCase.ImportReport;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProfileSource;
import dev.proofstack.core.port.out.ProfileWriter;

/**
 * Imports career data from any source (LinkedIn API, LinkedIn ZIP...) either as a new profile or merged into
 * an existing one ({@link ProfileMerge}). The result always goes to {@code target}; the existing profile is
 * read, never modified.
 */
public final class CareerImportService {

    private final ProfileSource reader;
    private final ProfileWriter writer;

    public CareerImportService(ProfileSource reader, ProfileWriter writer) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** @param mergeInto existing profile to update, or {@code null} to create a new one */
    public ImportReport importCareer(MemberProfileSource source, Path mergeInto, Path target, boolean overwrite) {
        if (mergeInto != null) {
            Targets.requireDifferent(mergeInto, target);
        }
        Profile existing = mergeInto == null ? null : reader.load(mergeInto); // fail fast before any network call
        Profile imported = source.fetchProfile();
        Profile result = existing == null ? imported : ProfileMerge.careerFrom(existing, imported);
        writer.write(result, target, overwrite);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("experience", result.experience().size());
        counts.put("education", result.education().size());
        counts.put("projects", result.projects().size());
        counts.put("skills", result.skills().size());
        return new ImportReport(TodoCounter.count(result), counts);
    }
}
