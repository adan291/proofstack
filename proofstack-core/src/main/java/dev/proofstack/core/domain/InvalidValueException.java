package dev.proofstack.core.domain;

/**
 * Thrown by a domain record when one of its own fields breaks an invariant.
 * {@link #field()} is relative to the record; adapters prefix it with the document path.
 */
public final class InvalidValueException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidValueException(String field, String message) {
        super(field + " " + message);
        this.field = field;
    }

    public String field() {
        return field;
    }

    /** The message without the field name. */
    public String reason() {
        return getMessage().substring(field.length() + 1);
    }
}
