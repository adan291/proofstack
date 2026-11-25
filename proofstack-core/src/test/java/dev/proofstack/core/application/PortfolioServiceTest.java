package dev.proofstack.core.application;

import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.in.ValidationResult;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.ArtifactSink;
import dev.proofstack.core.port.out.PortfolioRenderer;
import dev.proofstack.core.port.out.ProfileSource;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioServiceTest {

    private static final Path PROFILE = Path.of("profile.yaml");
    private static final Path OUT = Path.of("dist");

    private final Profile profile = Fixtures.profile(List.of(), List.of(), List.of(), List.of());
    private final PortfolioRenderer renderer = p -> List.of(new Artifact("index.html", p.person().name()));
    private final RecordingSink sink = new RecordingSink();

    @Test
    void buildRendersAndWritesArtifacts() {
        PortfolioService service = new PortfolioService(location -> profile, renderer, sink);

        List<Artifact> artifacts = service.build(PROFILE, OUT);

        assertThat(artifacts).containsExactly(new Artifact("index.html", "Ada Lovelace"));
        assertThat(sink.written).isEqualTo(artifacts);
        assertThat(sink.directory).isEqualTo(OUT);
    }

    @Test
    void buildDoesNotWriteWhenProfileIsInvalid() {
        ProfileSource invalid = location -> {
            throw new ProfileValidationException(new Violation("$.person.name", "must not be blank"));
        };
        PortfolioService service = new PortfolioService(invalid, renderer, sink);

        assertThatThrownBy(() -> service.build(PROFILE, OUT)).isInstanceOf(ProfileValidationException.class);
        assertThat(sink.written).isEmpty();
    }

    @Test
    void validateReturnsViolations() {
        ProfileSource invalid = location -> {
            throw new ProfileValidationException(new Violation("$.person.name", "must not be blank"));
        };
        ValidationResult result = new PortfolioService(invalid, renderer, sink).validate(PROFILE);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(Violation::path).containsExactly("$.person.name");
    }

    @Test
    void validateAcceptsValidProfile() {
        assertThat(new PortfolioService(location -> profile, renderer, sink).validate(PROFILE).valid()).isTrue();
    }

    @Test
    void artifactRejectsPathTraversal() {
        assertThatThrownBy(() -> new Artifact("../evil.html", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Artifact("/etc/passwd", "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Artifact("C:\\evil", "")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Artifact("assets\\app.js", "").relativePath()).isEqualTo("assets/app.js");
    }

    private static final class RecordingSink implements ArtifactSink {
        private final List<Artifact> written = new ArrayList<>();
        private Path directory;

        @Override
        public void write(Path outputDirectory, List<Artifact> artifacts) {
            this.directory = outputDirectory;
            written.addAll(artifacts);
        }
    }

    @org.junit.jupiter.api.Test
    void binaryArtifactsKeepTheirBytesAndCannotBeMutatedFromOutside() {
        byte[] data = {1, 2, 3};
        Artifact artifact = Artifact.binary("cv.docx", data);
        data[0] = 9;

        org.assertj.core.api.Assertions.assertThat(artifact.bytes()).containsExactly(1, 2, 3);
        org.assertj.core.api.Assertions.assertThat(artifact.isBinary()).isTrue();
        org.assertj.core.api.Assertions.assertThat(artifact.under("cv/").relativePath()).isEqualTo("cv/cv.docx");
        org.assertj.core.api.Assertions.assertThat(artifact.under("cv/").bytes()).containsExactly(1, 2, 3);
        org.assertj.core.api.Assertions.assertThat(new Artifact("a.txt", "\u00e1").bytes())
                .containsExactly((byte) 0xC3, (byte) 0xA1);
        assertThatThrownBy(() -> Artifact.binary("../evil.docx", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}