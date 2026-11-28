package dev.proofstack.core.domain.assist;

/** The path does not name a free-text field the assistant may write. */
public final class UnsupportedFieldException extends IllegalArgumentException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedFieldException(String message) {
        super(message);
    }
}
