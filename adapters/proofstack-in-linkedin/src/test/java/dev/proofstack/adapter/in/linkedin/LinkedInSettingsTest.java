package dev.proofstack.adapter.in.linkedin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LinkedInSettingsTest {

    @TempDir
    Path home;

    private Map<String, String> env() {
        return Map.of(LinkedInSettings.HOME_ENV, home.toString());
    }

    @Test
    void savedAppIsUsedWhenTheEnvironmentHasNone() throws Exception {
        assertThat(LinkedInSettings.configured(env())).isFalse();
        Path file = LinkedInSettings.save(env(), " id ", "secret", "");
        assertThat(file).isEqualTo(home.resolve("linkedin.properties"));
        Map<String, String> all = LinkedInSettings.withSaved(env());
        assertThat(all).containsEntry("LINKEDIN_CLIENT_ID", "id").containsEntry("LINKEDIN_CLIENT_SECRET", "secret")
                .containsEntry("LINKEDIN_REDIRECT_URI", LinkedInSettings.DEFAULT_REDIRECT);
        assertThat(LinkedInSettings.configured(env())).isTrue();
        assertThat(Files.readString(file)).doesNotContain("ACCESS_TOKEN");
    }

    @Test
    void environmentWinsOverTheFile() {
        LinkedInSettings.save(env(), "from-file", "secret", null);
        Map<String, String> env = Map.of(LinkedInSettings.HOME_ENV, home.toString(), "LINKEDIN_CLIENT_ID", "from-env");
        assertThat(LinkedInSettings.withSaved(env)).containsEntry("LINKEDIN_CLIENT_ID", "from-env");
    }

    @Test
    void onlyTheOwnerCanReadIt() throws Exception {
        Path file = LinkedInSettings.save(env(), "id", "secret", null);
        PosixFileAttributeView posix = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            assertThat(PosixFilePermissions.toString(posix.readAttributes().permissions())).isEqualTo("rw-------");
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            assertThat(acl.getAcl()).hasSize(1).allSatisfy(e -> assertThat(e.principal()).isEqualTo(acl.getOwner()));
        }
    }

    @Test
    void refusesIncompleteAppsAndRedirectsOutsideThisComputer() {
        assertThatThrownBy(() -> LinkedInSettings.save(env(), "id", " ", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LinkedInSettings.save(env(), "id", "secret", "https://evil.example/callback"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(home.resolve("linkedin.properties")).doesNotExist();
    }
}
