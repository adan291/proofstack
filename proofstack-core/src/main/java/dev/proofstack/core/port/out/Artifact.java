package dev.proofstack.core.port.out;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A generated output file, text or binary. The path is always relative and can never escape the output directory.
 *
 * @param relativePath where it goes, relative to the output directory
 * @param content      the text, or {@code ""} for a binary artifact
 * @param binary       the bytes of a binary artifact (e.g. {@code cv.docx}), or {@code null} for text
 */
public record Artifact(String relativePath, String content, byte[] binary) {

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
        binary = binary == null ? null : binary.clone();
    }

    /** A text artifact, written as UTF-8. */
    public Artifact(String relativePath, String content) {
        this(relativePath, content, null);
    }

    public static Artifact binary(String relativePath, byte[] bytes) {
        return new Artifact(relativePath, "", Objects.requireNonNull(bytes, "bytes"));
    }

    public boolean isBinary() {
        return binary != null;
    }

    /** What to write to disk: the bytes of a binary artifact, or the UTF-8 text. */
    public byte[] bytes() {
        return binary != null ? binary.clone() : content.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] binary() {
        return binary == null ? null : binary.clone();
    }

    /** The same artifact under {@code prefix}, e.g. {@code cv/}. */
    public Artifact under(String prefix) {
        return new Artifact(prefix + relativePath, content, binary);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Artifact a && relativePath.equals(a.relativePath) && content.equals(a.content)
                && Arrays.equals(binary, a.binary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath, content, Arrays.hashCode(binary));
    }

    @Override
    public String toString() {
        return "Artifact[" + relativePath + (binary != null ? ", " + binary.length + " bytes]" : "]");
    }
}
