package dev.proofstack.core.domain.knowledge;

/** The question is empty or too long. Maps to HTTP 400. */
public final class InvalidQuestionException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public InvalidQuestionException(String message) {
        super(message);
    }
}
