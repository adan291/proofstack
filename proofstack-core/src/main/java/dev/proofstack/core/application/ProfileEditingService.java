package dev.proofstack.core.application;

import java.util.List;
import java.util.Objects;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.readiness.PublishReadiness;
import dev.proofstack.core.port.in.EditProfileUseCase;
import dev.proofstack.core.port.out.ProfileDocumentStore;
import dev.proofstack.core.port.out.ProfileDocumentStore.VersionConflict;

/** Application service behind the editor. Validates before writing, so an invalid document never reaches disk. */
public final class ProfileEditingService implements EditProfileUseCase {

    private final ProfileDocumentStore store;

    public ProfileEditingService(ProfileDocumentStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Snapshot load() {
        ProfileDocumentStore.Stored stored = store.read();
        try {
            Profile profile = store.parse(stored.json());
            return new Snapshot(stored.json(), stored.version(), PublishReadiness.check(profile), List.of());
        } catch (ProfileValidationException e) {
            // The file was broken by hand: still open it, so the editor can fix it.
            return new Snapshot(stored.json(), stored.version(), List.of(), e.violations());
        }
    }

    @Override
    public SaveResult save(String json, String expectedVersion) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(expectedVersion, "expectedVersion");
        Profile profile;
        try {
            profile = store.parse(json);
        } catch (ProfileValidationException e) {
            return new Invalid(e.violations());
        }
        try {
            return new Saved(store.replace(json, expectedVersion), PublishReadiness.check(profile));
        } catch (VersionConflict e) {
            return new Conflict(e.currentVersion());
        } catch (ProfileValidationException e) {
            // The store can still refuse at write time (YAML that would not read back the same, a file too large):
            // nothing was written, and the user is told why instead of getting a generic error.
            return new Invalid(e.violations());
        }
    }
}
