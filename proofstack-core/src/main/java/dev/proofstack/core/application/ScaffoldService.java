package dev.proofstack.core.application;

import dev.proofstack.core.domain.StarterProfile;
import dev.proofstack.core.port.in.ScaffoldProfileUseCase;
import dev.proofstack.core.port.out.ProfileWriter;

import java.nio.file.Path;
import java.util.Objects;

/** Writes the starter profile through whatever {@link ProfileWriter} is wired (YAML today). */
public final class ScaffoldService implements ScaffoldProfileUseCase {

    private final ProfileWriter writer;

    public ScaffoldService(ProfileWriter writer) {
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public void scaffold(Path target, String name, String headline, String language, boolean overwrite) {
        writer.write(StarterProfile.create(name, headline, language), target, overwrite);
    }
}
