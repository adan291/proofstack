package dev.proofstack.core.port.out;

/**
 * A generated output file. The path is always relative and can never escape the output directory.
 */
public record Artifact(String relativePath, String content) {

    public Artifact {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("relativePath must not be blank");
        }
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")
                || ("/" + normalized + "/").contains("/../")) {
            throw new IllegalArgumentException("relativePath must be relative and inside the output dir: " + relativePath);
        }
        relativePath = normalized;
        content = content == null ? "" : content;
    }
}
