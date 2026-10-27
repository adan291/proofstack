package dev.proofstack.core.port.in;

import dev.proofstack.core.port.out.Artifact;

import java.nio.file.Path;
import java.util.List;

/** Driving port: load a profile, render it and write the result. */
public interface BuildPortfolioUseCase {

    /**
     * @return the artifacts that were written
     * @throws dev.proofstack.core.domain.ProfileValidationException if the profile is invalid
     */
    List<Artifact> build(Path profileLocation, Path outputDirectory);
}
