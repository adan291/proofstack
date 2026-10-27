package dev.proofstack.core.port.in;

import java.nio.file.Path;

/** Driving port: check a profile without producing output. */
public interface ValidateProfileUseCase {

    ValidationResult validate(Path profileLocation);
}
