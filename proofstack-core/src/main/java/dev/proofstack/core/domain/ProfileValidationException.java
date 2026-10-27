package dev.proofstack.core.domain;

import java.util.List;
import java.util.stream.Collectors;

/** Raised when a profile cannot be accepted. Carries every violation found, not just the first. */
public final class ProfileValidationException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final transient List<Violation> violations;

    public ProfileValidationException(List<Violation> violations) {
        super(format(violations));
        if (violations == null || violations.isEmpty()) {
            throw new IllegalArgumentException("A validation exception needs at least one violation");
        }
        this.violations = List.copyOf(violations);
    }

    public ProfileValidationException(Violation violation) {
        this(List.of(violation));
    }

    public List<Violation> violations() {
        return violations;
    }

    private static String format(List<Violation> violations) {
        if (violations == null) {
            return "";
        }
        return "Invalid profile:" + violations.stream()
                .map(v -> System.lineSeparator() + "  - " + v)
                .collect(Collectors.joining());
    }
}
