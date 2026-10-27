package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

import java.nio.file.Path;

/** Driven port: persists a profile document. */
public interface ProfileWriter {

    /**
     * @throws TargetExistsException if {@code target} exists and {@code overwrite} is false
     */
    void write(Profile profile, Path target, boolean overwrite);

    /** Raised instead of silently overwriting a user's hand-written profile. */
    final class TargetExistsException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public TargetExistsException(Path target) {
            super(target + " already exists (use --force to overwrite)");
        }
    }
}
