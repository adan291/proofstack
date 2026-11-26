package dev.proofstack.adapter.yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.out.ProfileDocumentStore;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Driven adapter: one {@code profile.yaml} as an editable document.
 *
 * <ul>
 *   <li>Only the file given at construction is ever read or written.</li>
 *   <li>Nothing invalid is written: every document is checked with the schema and the domain first.</li>
 *   <li>Writes are atomic (temporary file in the same directory, then a rename) and the previous file is kept as
 *       {@code profile.yaml.bak}.</li>
 *   <li>The version is the SHA-256 of the file bytes, so an edit made by another program is detected.</li>
 *   <li>The YAML is written from the document tree, not rebuilt from the domain: the user's key order survives, and
 *       so do the comments at the top of the file.</li>
 * </ul>
 */
public final class YamlProfileDocumentStore implements ProfileDocumentStore {

    private static final long MAX_SIZE_BYTES = 1024 * 1024;

    private final Path file;
    private final YamlProfileSource source = new YamlProfileSource();
    private final YAMLMapper yaml = YAMLMapper.builder(YAMLFactory.builder()
                    .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                    .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
                    .enable(YAMLWriteFeature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                    .enable(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR)
                    .stringQuotingChecker(TextQuoting.INSTANCE)
                    .build())
            .build();
    private final JsonMapper json = JsonMapper.builder().build();

    public YamlProfileDocumentStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    public Path file() {
        return file;
    }

    @Override
    public Stored read() {
        byte[] bytes = bytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        try {
            JsonNode tree = yaml.readTree(text);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                tree = JsonNodeFactory.instance.objectNode();
            }
            return new Stored(json.writeValueAsString(tree), version(bytes));
        } catch (JacksonException e) {
            var where = e.getLocation();
            String at = where == null ? "" : " (line " + where.getLineNr() + ", column " + where.getColumnNr() + ")";
            throw new ProfileValidationException(new Violation("$", "invalid YAML" + at + ": " + e.getOriginalMessage()));
        }
    }

    @Override
    public Profile parse(String document) {
        return source.fromJson(document);
    }

    @Override
    public synchronized String replace(String document, String expectedVersion) {
        source.fromJson(document); // never write something invalid, whoever the caller is
        byte[] current = bytes();
        String currentVersion = version(current);
        if (!currentVersion.equals(expectedVersion)) {
            throw new VersionConflict(currentVersion);
        }
        byte[] next;
        JsonNode tree;
        String body;
        try {
            tree = json.readTree(document);
            body = yaml.writeValueAsString(tree);
        } catch (JacksonException e) {
            throw new ProfileValidationException(new Violation("$", "invalid JSON: " + e.getOriginalMessage()));
        }
        requireSameWhenReadBack(tree, body);
        next = (header(new String(current, StandardCharsets.UTF_8)) + body).getBytes(StandardCharsets.UTF_8);
        write(current, next);
        return version(next);
    }

    /** Last line of defence: the YAML about to be written must read back as exactly the document that was validated. */
    private void requireSameWhenReadBack(JsonNode expected, String yamlText) {
        JsonNode readBack;
        try {
            readBack = yaml.readTree(yamlText);
        } catch (JacksonException e) {
            readBack = null;
        }
        if (!expected.equals(readBack)) {
            throw new ProfileValidationException(new Violation("$",
                    "the profile could not be written as YAML that reads back the same; nothing was saved"));
        }
    }

    private void write(byte[] previous, byte[] next) {
        Path dir = file.getParent();
        Path temp = null;
        try {
            temp = Files.createTempFile(dir, ".profile-", ".yaml.tmp");
            Files.write(temp, next);
            Files.write(dir.resolve(file.getFileName() + ".bak"), previous);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot save " + file.getFileName(), e);
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

    /** The leading comment block of the file (schema modeline, notes), kept as the user wrote it. */
    static String header(String text) {
        StringBuilder header = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (line.startsWith("#")) {
                header.append(line).append('\n');
            } else if (!line.isBlank()) {
                break;
            }
        }
        return header.isEmpty() ? "" : header + "\n";
    }

    private byte[] bytes() {
        try {
            if (Files.size(file) > MAX_SIZE_BYTES) {
                throw new ProfileValidationException(new Violation("$", "file is larger than 1 MiB"));
            }
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file.getFileName(), e);
        }
    }

    static String version(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
