package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.proofstack.adapter.out.llm.AiSettingsStore;
import dev.proofstack.adapter.yaml.YamlProfileJsonCodec;
import dev.proofstack.core.domain.assist.FieldPath;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The composition of the assistant: real configuration store, and the limits the core mirrors from the schema. */
class AiStudioAssistantTest {

    private static final String SECRET = "sk-secret-value-that-must-not-leak-123456";
    private final JsonMapper json = JsonMapper.builder().build();
    @TempDir
    Path home;

    private AiStudioAssistant assistant(Map<String, String> env) {
        return new AiStudioAssistant(new AiSettingsStore(AiSettingsStore.file(env), env), new YamlProfileJsonCodec());
    }

    @Test
    void theConfigurationLivesUnderTheProofstackHomeAndNeverLeaksTheKey() throws IOException {
        Map<String, String> env = Map.of("PROOFSTACK_HOME", home.toString(), "PROOFSTACK_AI_API_KEY", SECRET);
        AiStudioAssistant assistant = assistant(env);
        assertThat(assistant.status().path("configured").asBoolean()).isFalse();

        String warning = assistant.saveConfig(json.readTree("{\"preset\":\"openai\",\"apiKey\":\"" + SECRET + "-file\"}"));
        assertThat(warning).isNotNull();
        assertThat(Files.readString(home.resolve("ai.json"))).contains(SECRET + "-file");

        assertThat(assistant.status().toString()).doesNotContain(SECRET);
        assertThat(assistant.config().toString()).doesNotContain(SECRET);
        assertThat(assistant.status().path("configured").asBoolean()).isTrue();
        assertThat(assistant.status().path("keySource").asString()).isEqualTo("env");
        assertThat(assistant.status().path("local").asBoolean()).isFalse();
    }

    @Test
    void aRemoteProviderAsksForConsentOnceAndALocalOneNever() {
        Map<String, String> remote = Map.of("PROOFSTACK_HOME", home.toString(), "PROOFSTACK_AI_PROTOCOL", "openai",
                "PROOFSTACK_AI_BASE_URL", "https://api.example.com/v1", "PROOFSTACK_AI_MODEL", "m");
        AiStudioAssistant assistant = assistant(remote);
        assertThat(assistant.pendingConsent()).isEqualTo("api.example.com");
        assistant.consent("api.example.com");
        assertThat(assistant.pendingConsent()).isNull();
        assertThat(assistant(remote).pendingConsent()).as("remembered in ai.json").isNull();

        for (String local : List.of("http://127.0.0.1:1234/v1", "http://localhost:11434", "http://[::1]:8080/v1")) {
            Map<String, String> env = Map.of("PROOFSTACK_HOME", home.resolve("x").toString(), "PROOFSTACK_AI_PROTOCOL", "openai",
                    "PROOFSTACK_AI_BASE_URL", local, "PROOFSTACK_AI_MODEL", "m");
            assertThat(assistant(env).pendingConsent()).as(local).isNull();
            assertThat(assistant(env).status().path("local").asBoolean()).as(local).isTrue();
        }
    }

    @Test
    void theLimitsTheCoreAppliesAreTheOnesInTheProfileSchema() throws IOException {
        JsonNode schema;
        try (InputStream in = StudioCommand.class.getResourceAsStream("/proofstack/profile.schema.json")) {
            schema = json.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        for (String path : List.of("person.headline", "person.summary", "person.contactMessage", "projects[a].summary",
                "projects[a].problem", "projects[a].solution", "projects[a].learnings[0]", "projects[a].areaNotes.backend",
                "experience[a].highlights[0]", "evidence[a].title", "evidence[a].context", "evidence[a].action",
                "evidence[a].result", "labs[a].description")) {
            assertThat(FieldPath.parse(path).maxLength()).as(path).isEqualTo(schemaMaxLength(schema, path));
        }
    }

    /** Walks the schema along the path: a segment with [..] goes into the array's items. */
    private static int schemaMaxLength(JsonNode schema, String path) {
        JsonNode node = schema;
        for (String segment : path.split("\\.")) {
            String name = segment.replaceAll("\\[.*]$", "");
            node = resolve(schema, node).path("properties").path(name);
            if (segment.contains("[")) {
                node = resolve(schema, node).path("items");
            }
        }
        return resolve(schema, node).path("maxLength").asInt(-1);
    }

    private static JsonNode resolve(JsonNode schema, JsonNode node) {
        String ref = node.path("$ref").asString("");
        return ref.startsWith("#/$defs/") ? resolve(schema, schema.path("$defs").path(ref.substring("#/$defs/".length()))) : node;
    }
}
