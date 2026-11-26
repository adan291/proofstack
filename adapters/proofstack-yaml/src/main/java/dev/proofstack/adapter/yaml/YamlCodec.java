package dev.proofstack.adapter.yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * The one place that knows how profile.yaml is read and written (ADR-016): every reader and writer of this module
 * goes through it, so a formatting rule (quotes, indentation, the read-back check) cannot exist in two versions.
 */
final class YamlCodec {

    /** Reads and writes. Unknown properties are the schema's business, not the mapper's. */
    static final YAMLMapper YAML = YAMLMapper.builder(YAMLFactory.builder()
                    .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                    .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                    .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                    .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                    .stringQuotingChecker(TextQuoting.INSTANCE)
                    .build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private YamlCodec() {
    }

    /**
     * The YAML for {@code tree}, checked: it must read back as exactly {@code tree}, or nothing is written (H1).
     *
     * @throws ProfileValidationException if it would not read back the same
     */
    static String toYaml(JsonNode tree) {
        String yaml = YAML.writeValueAsString(tree);
        JsonNode readBack;
        try {
            readBack = YAML.readTree(yaml);
        } catch (JacksonException e) {
            readBack = null;
        }
        if (!tree.equals(readBack)) {
            throw new ProfileValidationException(new Violation("$",
                    "the profile could not be written as YAML that reads back the same; nothing was saved"));
        }
        return yaml;
    }

    /**
     * Temporary file in the same directory, then a rename: a failure never leaves a half-written profile, and a link
     * to the old file is replaced rather than written through.
     *
     * @param backup where to keep {@code previous} first, or {@code null} for no backup
     */
    static void writeAtomically(Path target, String content, Path backup, byte[] previous) {
        Path dir = target.toAbsolutePath().getParent();
        Path temp = null;
        try {
            Files.createDirectories(dir);
            temp = Files.createTempFile(dir, ".profile-", ".yaml.tmp");
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            if (backup != null) {
                Files.write(backup, previous);
            }
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target.getFileName(), e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // the rename already consumed it, or there is nothing more to clean
                }
            }
        }
    }
}
