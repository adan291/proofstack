package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

/**
 * Driven port: the profile file seen as an editable document. The document travels as JSON text with the same
 * shape as {@code profile.yaml}, so the core never depends on a serialisation library.
 */
public interface ProfileDocumentStore {

    /** The document on disk and its version (a hash of the file bytes). */
    Stored read();

    /**
     * Validates a document against the schema and the domain rules without touching the disk.
     *
     * @throws dev.proofstack.core.domain.ProfileValidationException if it is not a valid profile
     */
    Profile parse(String json);

    /**
     * Replaces the file atomically, keeping a backup of the previous one.
     *
     * @param expectedVersion the version the editor started from
     * @return the new version
     * @throws VersionConflict if the file changed on disk since {@code expectedVersion}
     */
    String replace(String json, String expectedVersion);

    record Stored(String json, String version) {
    }

    /** The file changed on disk since it was read: saving now would overwrite someone else's edit. */
    final class VersionConflict extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String currentVersion;

        public VersionConflict(String currentVersion) {
            super("the profile changed on disk; reload before saving");
            this.currentVersion = currentVersion;
        }

        public String currentVersion() {
            return currentVersion;
        }
    }
}
