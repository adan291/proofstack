package dev.proofstack.cli;

import java.util.Locale;
import java.util.Optional;

import dev.proofstack.adapter.in.studio.StudioAssistant;
import dev.proofstack.adapter.out.llm.AiConfigException;
import dev.proofstack.adapter.out.llm.AiSettings;
import dev.proofstack.adapter.out.llm.AiSettingsStore;
import dev.proofstack.adapter.out.llm.LlmAssistants;
import dev.proofstack.core.application.TextAssistantService;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Composition root piece: Studio's assistant, on top of the configuration store and the LLM adapter (ADR-017).
 * The provider is resolved on every call, so a change in the AI settings applies to the next request.
 */
final class AiStudioAssistant implements StudioAssistant {

    private final AiSettingsStore store;
    private final ProfileJsonCodec codec;
    private final JsonMapper json = JsonMapper.builder().build();

    AiStudioAssistant(AiSettingsStore store, ProfileJsonCodec codec) {
        this.store = store;
        this.codec = codec;
    }

    @Override
    public ObjectNode status() {
        return store.status();
    }

    @Override
    public ObjectNode config() {
        return store.view();
    }

    @Override
    public String saveConfig(JsonNode update) {
        try {
            return store.save(update);
        } catch (AiConfigException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public ObjectNode test() {
        Optional<AiSettings> settings = resolve();
        ObjectNode out = json.createObjectNode();
        if (settings.isEmpty()) {
            return out.put("ok", false).put("model", "").put("millis", 0).put("error", "no AI provider is configured");
        }
        LlmAssistants.ConnectionResult r = LlmAssistants.test(settings.get());
        return out.put("ok", r.ok()).put("model", r.model()).put("millis", r.millis()).put("error", r.error());
    }

    @Override
    public String pendingConsent() {
        Optional<AiSettings> settings;
        try {
            settings = store.resolve();
        } catch (AiConfigException e) {
            return null;
        }
        return settings.filter(s -> !s.local() && !store.accepted(s.providerHost())).map(AiSettings::providerHost).orElse(null);
    }

    @Override
    public void consent(String host) {
        store.acceptHost(host);
    }

    @Override
    public Suggestion suggest(Profile saved, FieldPath path, String currentText, String request, AssistMode mode,
                              Locale targetLanguage) {
        AiSettings settings = resolve().orElseThrow(() -> new IllegalStateException("no AI provider is configured"));
        return new TextAssistantService(LlmAssistants.drafter(settings), codec)
                .suggest(saved, path, currentText, request, mode, targetLanguage);
    }

    private Optional<AiSettings> resolve() {
        try {
            return store.resolve();
        } catch (AiConfigException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
