package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;

import java.nio.file.Path;

/** Driven port: where profiles come from (YAML today, LinkedIn export or GitHub tomorrow). */
public interface ProfileSource {

    /**
     * @throws ProfileValidationException if the document is not a valid profile
     * @throws java.io.UncheckedIOException if the location cannot be read
     */
    Profile load(Path location);
}
