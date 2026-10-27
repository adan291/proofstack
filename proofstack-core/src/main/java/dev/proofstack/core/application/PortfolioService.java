package dev.proofstack.core.application;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.port.in.BuildPortfolioUseCase;
import dev.proofstack.core.port.in.ValidateProfileUseCase;
import dev.proofstack.core.port.in.ValidationResult;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.ArtifactSink;
import dev.proofstack.core.port.out.PortfolioRenderer;
import dev.proofstack.core.port.out.ProfileSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Application service orchestrating the ports. Contains no I/O of its own. */
public final class PortfolioService implements ValidateProfileUseCase, BuildPortfolioUseCase {

    private final ProfileSource source;
    private final PortfolioRenderer renderer;
    private final ArtifactSink sink;

    public PortfolioService(ProfileSource source, PortfolioRenderer renderer, ArtifactSink sink) {
        this.source = Objects.requireNonNull(source, "source");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public ValidationResult validate(Path profileLocation) {
        try {
            source.load(profileLocation);
            return ValidationResult.ok();
        } catch (ProfileValidationException e) {
            return new ValidationResult(e.violations());
        }
    }

    @Override
    public List<Artifact> build(Path profileLocation, Path outputDirectory) {
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Profile profile = source.load(profileLocation);
        List<Artifact> artifacts = renderer.render(profile);
        sink.write(outputDirectory, artifacts);
        return artifacts;
    }
}
