package dev.proofstack.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.in.EditProfileUseCase.Conflict;
import dev.proofstack.core.port.in.EditProfileUseCase.Invalid;
import dev.proofstack.core.port.in.EditProfileUseCase.Saved;
import dev.proofstack.core.port.in.EditProfileUseCase.Snapshot;
import dev.proofstack.core.port.out.ProfileDocumentStore;

class ProfileEditingServiceTest {

    /** In-memory store: a JSON document is "valid" unless it contains the word INVALID. */
    private static final class FakeStore implements ProfileDocumentStore {
        String json = "{\"person\":{\"name\":\"Ada\"}}";
        int version = 1;
        int writes = 0;

        @Override
        public Stored read() {
            return new Stored(json, "v" + version);
        }

        @Override
        public Profile parse(String document) {
            if (document.contains("INVALID")) {
                throw new ProfileValidationException(new Violation("$.person.name", "must not be blank"));
            }
            String summary = document.contains("TODO") ? "TODO: write it" : "Builds backends.";
            return new Profile(new PersonalInfo("Ada", "Backend Engineer", summary, null, null, List.of()),
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        @Override
        public String replace(String document, String expectedVersion) {
            if (!expectedVersion.equals("v" + version)) {
                throw new VersionConflict("v" + version);
            }
            json = document;
            writes++;
            return "v" + (++version);
        }
    }

    private final FakeStore store = new FakeStore();
    private final ProfileEditingService service = new ProfileEditingService(store);

    @Test
    void loadReturnsTheDocumentItsVersionAndWhatDoctorSays() {
        Snapshot snapshot = service.load();

        assertThat(snapshot.json()).isEqualTo(store.json);
        assertThat(snapshot.version()).isEqualTo("v1");
        assertThat(snapshot.invalid()).isEmpty();
        assertThat(snapshot.findings()).isNotNull();
    }

    @Test
    void aFileBrokenByHandStillOpensSoTheEditorCanFixIt() {
        store.json = "{\"INVALID\":true}";

        Snapshot snapshot = service.load();

        assertThat(snapshot.json()).isEqualTo(store.json);
        assertThat(snapshot.invalid()).extracting(Violation::path).containsExactly("$.person.name");
        assertThat(snapshot.findings()).isEmpty();
    }

    @Test
    void aValidDocumentIsSavedAndReportsTheNewVersionAndFindings() {
        var result = service.save("{\"person\":{\"summary\":\"TODO\"}}", "v1");

        assertThat(result).isInstanceOf(Saved.class);
        Saved saved = (Saved) result;
        assertThat(saved.version()).isEqualTo("v2");
        assertThat(saved.findings()).anyMatch(f -> f.path().equals("$.person.summary"));
        assertThat(store.writes).isEqualTo(1);
    }

    @Test
    void anInvalidDocumentIsRejectedAndNothingIsWritten() {
        var result = service.save("{\"INVALID\":1}", "v1");

        assertThat(result).isInstanceOf(Invalid.class);
        assertThat(((Invalid) result).violations()).extracting(Violation::path).containsExactly("$.person.name");
        assertThat(store.writes).isZero();
    }

    @Test
    void aStaleVersionIsAConflictAndNothingIsWritten() {
        var result = service.save("{}", "v0");

        assertThat(result).isEqualTo(new Conflict("v1"));
        assertThat(store.writes).isZero();
    }
}
