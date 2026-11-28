package dev.proofstack.adapter.in.studio;

import java.util.Locale;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * What the text assistant needs from the outside (ADR-017). Studio does not know any provider: the composition root
 * implements this on top of the outbound LLM adapter, and a Studio without it simply has no AI buttons.
 * No method returns a key. Invalid configuration is an {@link IllegalArgumentException} with a message for the user.
 */
public interface StudioAssistant {

    /** {@code {configured, protocol, model, local, keySource}} and nothing else. */
    ObjectNode status();

    /** The stored configuration, the effective one, the environment overrides and the presets. No key. */
    ObjectNode config();

    /**
     * @return a warning for the user (the key is stored in the file), or {@code null}
     * @throws IllegalArgumentException if a value is not valid; nothing is written then
     */
    String saveConfig(JsonNode update);

    /** One tiny request to the provider: {@code {ok, model, millis, error}}. Sends no profile data. */
    ObjectNode test();

    /** The host the profile would be sent to and the author has not accepted yet; {@code null} when none. */
    String pendingConsent();

    /** Remembers that the author accepted sending the profile to {@code host}. */
    void consent(String host);

    /**
     * @throws IllegalStateException if no provider is configured
     * @throws dev.proofstack.core.domain.content.ContentGenerationException if the provider fails
     */
    Suggestion suggest(Profile saved, FieldPath path, String currentText, String request, AssistMode mode,
                       Locale targetLanguage);
}
