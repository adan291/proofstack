package dev.proofstack.core.application;

import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.in.ImportProfileUseCase.ImportReport;
import dev.proofstack.core.port.out.ProfileWriter;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportServiceTest {

    private final Profile imported = new Profile(
            new PersonalInfo("Ada", "TODO: headline", "TODO: summary", null, null, List.of()),
            List.of(Fixtures.experience("x", List.of())), List.of(), List.of(Fixtures.project("p")),
            List.of(), List.of(), List.of());

    @Test
    void writesProfileAndReportsTodos() {
        Profile[] written = new Profile[1];
        ProfileWriter writer = (profile, target, overwrite) -> written[0] = profile;

        ImportReport report = new ImportService(source -> imported, writer)
                .importProfile(Path.of("export.zip"), Path.of("profile.yaml"), false);

        assertThat(written[0]).isSameAs(imported);
        assertThat(report.todoCount()).isEqualTo(2);
        assertThat(report.counts()).containsEntry("experience", 1).containsEntry("projects", 1);
    }

    @Test
    void propagatesTargetExists() {
        ProfileWriter writer = (profile, target, overwrite) -> {
            throw new ProfileWriter.TargetExistsException(target);
        };

        assertThatThrownBy(() -> new ImportService(source -> imported, writer)
                .importProfile(Path.of("export.zip"), Path.of("profile.yaml"), false))
                .isInstanceOf(ProfileWriter.TargetExistsException.class)
                .hasMessageContaining("--force");
    }
}
