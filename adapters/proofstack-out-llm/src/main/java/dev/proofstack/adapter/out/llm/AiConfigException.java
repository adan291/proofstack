package dev.proofstack.adapter.out.llm;

/** The assistant configuration is unusable. The message is safe to show: it never contains a key or a path. */
public final class AiConfigException extends RuntimeException {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public AiConfigException(String message) {
        super(message);
    }

    public AiConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
