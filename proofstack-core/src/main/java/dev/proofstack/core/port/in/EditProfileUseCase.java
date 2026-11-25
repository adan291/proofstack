package dev.proofstack.core.port.in;

import java.util.List;

import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.readiness.PublishReadiness.Finding;

/** Driving port: read and save the profile from an editor, never leaving an invalid file on disk. */
public interface EditProfileUseCase {

    Snapshot load();

    /**
     * @param json            the whole document, same shape as {@code profile.yaml}
     * @param expectedVersion the {@link Snapshot#version()} the editor started from
     */
    SaveResult save(String json, String expectedVersion);

    /**
     * @param findings what {@code proofstack doctor} would report; empty when the stored file is not even valid
     * @param invalid  why the stored file is invalid (edited by hand, for instance), or empty
     */
    record Snapshot(String json, String version, List<Finding> findings, List<Violation> invalid) {

        public Snapshot {
            findings = List.copyOf(findings);
            invalid = List.copyOf(invalid);
        }
    }

    sealed interface SaveResult {
    }

    record Saved(String version, List<Finding> findings) implements SaveResult {

        public Saved {
            findings = List.copyOf(findings);
        }
    }

    /** Nothing was written. */
    record Invalid(List<Violation> violations) implements SaveResult {

        public Invalid {
            violations = List.copyOf(violations);
        }
    }

    /** Nothing was written: the file changed on disk since the editor loaded it. */
    record Conflict(String currentVersion) implements SaveResult {
    }
}
