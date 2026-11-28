package dev.proofstack.adapter.out.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiSettingsStoreTest {

    private static final String SECRET = "sk-very-secret-value-123456";

    private final ObjectMapper json = new ObjectMapper();
    @TempDir
    Path dir;

    private AiSettingsStore store(Map<String, String> env) {
        return new AiSettingsStore(dir.resolve("home").resolve("ai.json"), env);
    }

    private void file(String content) throws IOException {
        Path f = dir.resolve("home").resolve("ai.json");
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
    }

    @Test
    void withoutFileOrEnvironmentThereIsNoProvider() {
        assertThat(store(Map.of()).resolve()).isEmpty();
        assertThat(store(Map.of()).status().path("configured").asBoolean()).isFalse();
    }

    @Test
    void geminiKeyAloneDoesNotTurnTheAssistantOn() {
        assertThat(store(Map.of("GEMINI_API_KEY", SECRET, "OLLAMA_HOST", "127.0.0.1:11434")).resolve()).isEmpty();
    }

    @Test
    void aPresetFillsTheDefaultsAndTheNamedVariableGivesTheKey() throws IOException {
        file("{\"preset\":\"openai\"}");
        AiSettings s = store(Map.of("OPENAI_API_KEY", SECRET)).resolve().orElseThrow();
        assertThat(s.protocol()).isEqualTo(Protocol.OPENAI);
        assertThat(s.baseUrl().toString()).isEqualTo("https://api.openai.com/v1");
        assertThat(s.model()).isNotBlank();
        assertThat(s.apiKey()).isEqualTo(SECRET);
        assertThat(s.keySource()).isEqualTo("env");
        assertThat(s.local()).isFalse();
        assertThat(s.requestHeaders()).containsEntry("Authorization", "Bearer " + SECRET);
    }

    @Test
    void azureUsesTheApiKeyHeaderAndOpenRouterBringsItsExtraHeaders() throws IOException {
        file("{\"preset\":\"azure\",\"baseUrl\":\"https://r.openai.azure.com/openai/deployments/d?api-version=1\",\"model\":\"d\"}");
        AiSettings azure = store(Map.of("AZURE_OPENAI_API_KEY", SECRET)).resolve().orElseThrow();
        assertThat(azure.requestHeaders()).containsEntry("api-key", SECRET).doesNotContainKey("Authorization");

        file("{\"preset\":\"openrouter\"}");
        AiSettings router = store(Map.of("OPENROUTER_API_KEY", SECRET)).resolve().orElseThrow();
        assertThat(router.requestHeaders()).containsKey("HTTP-Referer");
    }

    @Test
    void fileBeatsPresetAndEnvironmentBeatsFile() throws IOException {
        file("{\"preset\":\"openai\",\"model\":\"from-file\",\"baseUrl\":\"https://file.example/v1\",\"apiKeyEnv\":\"MY_KEY\"}");
        AiSettings fromFile = store(Map.of("MY_KEY", "k1", "OPENAI_API_KEY", "ignored")).resolve().orElseThrow();
        assertThat(fromFile.model()).isEqualTo("from-file");
        assertThat(fromFile.baseUrl().getHost()).isEqualTo("file.example");
        assertThat(fromFile.apiKey()).isEqualTo("k1");

        AiSettings fromEnv = store(Map.of(AiSettingsStore.ENV_PROTOCOL, "anthropic",
                AiSettingsStore.ENV_BASE_URL, "https://env.example", AiSettingsStore.ENV_MODEL, "from-env",
                AiSettingsStore.ENV_API_KEY, "k2", "MY_KEY", "k1")).resolve().orElseThrow();
        assertThat(fromEnv.protocol()).isEqualTo(Protocol.ANTHROPIC);
        assertThat(fromEnv.baseUrl().getHost()).isEqualTo("env.example");
        assertThat(fromEnv.model()).isEqualTo("from-env");
        assertThat(fromEnv.apiKey()).isEqualTo("k2");
        assertThat(fromEnv.authHeader()).isEqualTo("x-api-key");
    }

    @Test
    void environmentAloneConfiguresWithoutAnyFile() {
        AiSettings s = store(Map.of(AiSettingsStore.ENV_PROTOCOL, "openai",
                AiSettingsStore.ENV_BASE_URL, "http://localhost:1234/v1", AiSettingsStore.ENV_MODEL, "m")).resolve().orElseThrow();
        assertThat(s.local()).isTrue();
        assertThat(s.apiKey()).isNull();
        assertThat(s.keySource()).isEqualTo("none");
        assertThat(s.requestHeaders()).isEmpty();
    }

    @Test
    void geminiAndOllamaKeepTheirHistoricVariables() throws IOException {
        file("{\"protocol\":\"gemini\"}");
        AiSettings gemini = store(Map.of("GEMINI_API_KEY", SECRET)).resolve().orElseThrow();
        assertThat(gemini.apiKey()).isEqualTo(SECRET);
        assertThat(gemini.baseUrl().getHost()).isEqualTo("generativelanguage.googleapis.com");

        file("{\"protocol\":\"ollama\"}");
        AiSettings ollama = store(Map.of("OLLAMA_HOST", "127.0.0.1:11500")).resolve().orElseThrow();
        assertThat(ollama.baseUrl().toString()).isEqualTo("http://127.0.0.1:11500");
        assertThat(ollama.local()).isTrue();
    }

    @Test
    void aKeyStoredInTheFileIsUsedAndReportedAsFile() throws IOException {
        file("{\"preset\":\"openai\",\"apiKey\":\"" + SECRET + "\"}");
        AiSettings s = store(Map.of()).resolve().orElseThrow();
        assertThat(s.apiKey()).isEqualTo(SECRET);
        assertThat(s.keySource()).isEqualTo("file");
    }

    @Test
    void theKeyNeverAppearsInStatusViewOrToString() throws IOException {
        file("{\"preset\":\"openai\",\"apiKey\":\"" + SECRET + "\"}");
        AiSettingsStore store = store(Map.of(AiSettingsStore.ENV_API_KEY, SECRET + "-env"));
        assertThat(store.status().toString()).doesNotContain(SECRET);
        assertThat(store.view().toString()).doesNotContain(SECRET);
        assertThat(store.view().at("/file/hasKey").asBoolean()).isTrue();
        assertThat(store.resolve().orElseThrow().toString()).doesNotContain(SECRET);
        assertThat(store.status().propertyNames()).containsExactlyInAnyOrder("configured", "protocol", "model", "local",
                "keySource");
    }

    @Test
    void viewListsEnvironmentOverridesAndEveryPreset() {
        JsonNode view = store(Map.of(AiSettingsStore.ENV_MODEL, "x")).view();
        assertThat(view.path("envOverrides").toString()).contains("model");
        assertThat(view.path("presets").toString()).contains("openai", "anthropic", "gemini", "mistral", "groq",
                "openrouter", "deepseek", "azure", "ollama", "lmstudio", "custom");
    }

    @Test
    void plainHttpToAnotherHostNeedsExpressConfirmation() throws IOException {
        file("{\"protocol\":\"openai\",\"baseUrl\":\"http://llm.example.com/v1\",\"model\":\"m\"}");
        assertThatThrownBy(() -> store(Map.of()).resolve()).isInstanceOf(AiConfigException.class)
                .hasMessageContaining("unencrypted");
        assertThat(store(Map.of()).status().path("configured").asBoolean()).isFalse();
        assertThat(store(Map.of()).view().path("problem").asString()).contains("unencrypted");

        file("{\"protocol\":\"openai\",\"baseUrl\":\"http://llm.example.com/v1\",\"model\":\"m\",\"allowInsecureHttp\":true}");
        assertThat(store(Map.of()).resolve()).isPresent();
    }

    @Test
    void onlyHttpAndHttpsAreAccepted() throws IOException {
        file("{\"protocol\":\"openai\",\"baseUrl\":\"file:///etc/passwd\",\"model\":\"m\"}");
        assertThatThrownBy(() -> store(Map.of()).resolve()).isInstanceOf(AiConfigException.class)
                .hasMessageContaining("http");
        file("{\"protocol\":\"openai\",\"baseUrl\":\"https://user:pw@example.com\",\"model\":\"m\"}");
        assertThatThrownBy(() -> store(Map.of()).resolve()).isInstanceOf(AiConfigException.class);
    }

    @Test
    void invalidJsonAndUnknownProtocolAreReportedWithoutPathsOrContent() throws IOException {
        file("{ not json " + SECRET);
        assertThatThrownBy(() -> store(Map.of()).resolve()).isInstanceOf(AiConfigException.class)
                .hasMessage("ai.json is not valid JSON");
        file("{\"protocol\":\"cohere\"}");
        assertThatThrownBy(() -> store(Map.of()).resolve()).hasMessageContaining("openai, anthropic, gemini, ollama");
    }

    @Test
    void saveWritesTheFileKeepsTheKeyUnlessToldAndRejectsBadValues() throws IOException {
        AiSettingsStore store = store(Map.of());
        String warning = store.save(json.readTree("{\"preset\":\"openai\",\"model\":\"m1\",\"apiKey\":\"" + SECRET + "\"}"));
        assertThat(warning).contains("plain text");
        assertThat(Files.readString(dir.resolve("home").resolve("ai.json"))).contains(SECRET);

        assertThat(store.save(json.readTree("{\"model\":\"m2\"}"))).isNotNull();
        assertThat(store.resolve().orElseThrow().apiKey()).isEqualTo(SECRET);
        assertThat(store.resolve().orElseThrow().model()).isEqualTo("m2");

        assertThat(store.save(json.readTree("{\"clearKey\":true}"))).isNull();
        assertThat(store.resolve().orElseThrow().apiKey()).isNull();

        assertThatThrownBy(() -> store.save(json.readTree("{\"apiKeyEnv\":\"sk-abc def\"}")))
                .hasMessageContaining("NAME");
        assertThatThrownBy(() -> store.save(json.readTree("{\"baseUrl\":\"ftp://x\"}"))).isInstanceOf(AiConfigException.class);
        assertThatThrownBy(() -> store.save(json.readTree("{\"headers\":{\"X\\nBad\":\"v\"}}"))).isInstanceOf(AiConfigException.class);
        assertThatThrownBy(() -> store.save(json.readTree("{\"temperature\":9}"))).isInstanceOf(AiConfigException.class);
        assertThat(store.resolve().orElseThrow().model()).isEqualTo("m2");
    }

    @Test
    void savingDoesNotLetTheBrowserEditAcceptedHosts() throws IOException {
        AiSettingsStore store = store(Map.of());
        store.save(json.readTree("{\"preset\":\"openai\",\"acceptedHosts\":[\"api.openai.com\"]}"));
        assertThat(store.accepted("api.openai.com")).isFalse();
        store.acceptHost("API.openai.com");
        assertThat(store.accepted("api.openai.com")).isTrue();
        store.save(json.readTree("{\"model\":\"m\"}"));
        assertThat(store.accepted("api.openai.com")).isTrue();
    }

    @Test
    void presetsAreJustDataAndEveryOneResolvesToAProtocol() {
        for (String id : store(Map.of()).presetIds()) {
            JsonNode preset = store(Map.of()).view().path("presets");
            assertThat(preset.toString()).contains("\"" + id + "\"");
        }
        assertThat(store(Map.of()).presetIds()).hasSize(11);
    }
}
