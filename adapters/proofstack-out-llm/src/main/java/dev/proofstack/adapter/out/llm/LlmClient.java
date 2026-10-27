package dev.proofstack.adapter.out.llm;

import tools.jackson.databind.JsonNode;

/**
 * Minimal provider abstraction: one system prompt, one user prompt, JSON constrained by a schema.
 * Implementations translate transport problems into
 * {@link dev.proofstack.core.domain.content.ContentGenerationException.Kind#PROVIDER_UNAVAILABLE}.
 */
interface LlmClient {

    /** @return the raw text the model produced (expected to be JSON) */
    String completeJson(String systemPrompt, String userPrompt, JsonNode schema);

    /** Human readable "provider/model" for messages. */
    String describe();
}
