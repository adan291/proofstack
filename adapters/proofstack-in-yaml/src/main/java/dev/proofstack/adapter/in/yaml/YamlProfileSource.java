package dev.proofstack.adapter.in.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.out.ProfileSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Driven adapter: reads {@code profile.yaml}, validates it against the public JSON Schema
 * and maps it to the domain.
 *
 * <p>Jackson's YAML support only produces a JSON tree (no arbitrary type instantiation),
 * so loading untrusted profiles does not allow deserialization gadgets.
 */
public final class YamlProfileSource implements ProfileSource {

    static final String SCHEMA_RESOURCE = "/proofstack/profile.schema.json";
    private static final long MAX_SIZE_BYTES = 1024 * 1024;

    private final YAMLMapper mapper;
    private final Schema schema;

    public YamlProfileSource() {
        this.mapper = YAMLMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.schema = loadSchema();
    }

    @Override
    public Profile load(Path location) {
        JsonNode tree = readTree(location);

        List<Error> errors = schema.validate(tree);
        if (!errors.isEmpty()) {
            throw new ProfileValidationException(errors.stream()
                    .map(YamlProfileSource::toViolation)
                    .distinct()
                    .sorted(Comparator.comparing(Violation::path).thenComparing(Violation::message))
                    .toList());
        }

        try {
            ProfileDocument document = mapper.treeToValue(tree, ProfileDocument.class);
            return new DocumentMapper().map(document);
        } catch (JacksonException e) {
            throw new ProfileValidationException(new Violation("$", e.getOriginalMessage()));
        }
    }

    private JsonNode readTree(Path location) {
        String content;
        try {
            if (Files.size(location) > MAX_SIZE_BYTES) {
                throw new ProfileValidationException(new Violation("$", "file is larger than 1 MiB"));
            }
            content = Files.readString(location, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read profile " + location, e);
        }
        try {
            JsonNode tree = mapper.readTree(content);
            if (tree == null || tree.isMissingNode() || tree.isNull()) {
                throw new ProfileValidationException(new Violation("$", "document is empty"));
            }
            return tree;
        } catch (JacksonException e) {
            TokenStreamLocation where = e.getLocation();
            String at = where == null ? "" : " (line " + where.getLineNr() + ", column " + where.getColumnNr() + ")";
            throw new ProfileValidationException(new Violation("$", "invalid YAML" + at + ": " + e.getOriginalMessage()));
        }
    }

    private static Violation toViolation(Error error) {
        String path = error.getInstanceLocation().toString();
        String text = error.getMessage();
        // networknt may prefix messages with the instance location; keep only the reason.
        if (text.startsWith(path + ": ")) {
            text = text.substring(path.length() + 2);
        }
        return new Violation(path, text);
    }

    static Schema loadSchema(String resource) {
        try (InputStream in = YamlProfileSource.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema resource " + resource);
            }
            SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                    .pathType(PathType.LEGACY)   // "$.projects[0].id", the format used in our messages
                    .locale(Locale.ENGLISH)
                    .build();
            return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    builder -> builder.schemaRegistryConfig(config)).getSchema(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Schema loadSchema() {
        return loadSchema(SCHEMA_RESOURCE);
    }
}
