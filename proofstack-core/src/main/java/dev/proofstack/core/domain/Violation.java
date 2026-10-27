package dev.proofstack.core.domain;

/**
 * A single validation problem located in the profile document.
 *
 * @param path    JSONPath-like location, e.g. {@code $.projects[0].name}
 * @param message human readable reason
 */
public record Violation(String path, String message) {

    public Violation {
        path = path == null || path.isBlank() ? "$" : path;
        message = message == null ? "" : message;
    }

    @Override
    public String toString() {
        return path + ": " + message;
    }
}
