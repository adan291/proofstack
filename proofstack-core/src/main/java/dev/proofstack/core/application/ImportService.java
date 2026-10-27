package dev.proofstack.core.application;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.port.in.ImportProfileUseCase;
import dev.proofstack.core.port.out.ProfileImporter;
import dev.proofstack.core.port.out.ProfileWriter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Imports a profile from an external source and writes it as a draft for the user to complete. */
public final class ImportService implements ImportProfileUseCase {

    private final ProfileImporter importer;
    private final ProfileWriter writer;

    public ImportService(ProfileImporter importer, ProfileWriter writer) {
        this.importer = Objects.requireNonNull(importer, "importer");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public ImportReport importProfile(Path source, Path target, boolean overwrite) {
        Profile profile = importer.importProfile(source);
        writer.write(profile, target, overwrite);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("experience", profile.experience().size());
        counts.put("education", profile.education().size());
        counts.put("projects", profile.projects().size());
        counts.put("skills", profile.skills().size());
        return new ImportReport(TodoCounter.count(profile), counts);
    }
}
