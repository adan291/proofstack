package dev.proofstack.core.domain.content;

/** Generation failed. There is never a fake fallback: callers get this instead (ADR-006). */
public final class ContentGenerationException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public enum Kind {
        /** The requested subject id does not exist in the profile. */
        UNKNOWN_SUBJECT,
        /** Missing credentials, connection refused, HTTP error, timeout... */
        PROVIDER_UNAVAILABLE,
        /** The model answered something that breaks the contract (schema, length). */
        INVALID_OUTPUT
    }

    private final Kind kind;

    public ContentGenerationException(String message, Kind kind) {
        super(message);
        this.kind = kind;
    }

    public ContentGenerationException(String message, Kind kind, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
