package dev.proofstack.core.port.out;

/** Failure talking to an external source of career data (GitHub, LinkedIn...): shared by every import port. */
public final class ImportException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public enum Kind { NOT_FOUND, RATE_LIMITED, UNAVAILABLE, INVALID_INPUT }

    private final Kind kind;

    public ImportException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public ImportException(String message, Kind kind, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
