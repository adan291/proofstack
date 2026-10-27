package dev.proofstack.core.port.in;

import dev.proofstack.core.domain.Violation;

import java.util.List;

/** Outcome of validating a profile. Empty violations means valid. */
public record ValidationResult(List<Violation> violations) {

    public ValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    public boolean valid() {
        return violations.isEmpty();
    }
}
