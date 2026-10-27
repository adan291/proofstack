package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.content.ContentBrief;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.GeneratedContent;

/** Driven port: writes a draft from a brief (LLM today, maybe a human-in-the-loop tool tomorrow). */
public interface ContentGenerator {

    /**
     * @throws ContentGenerationException if the provider is unavailable or the output is invalid
     */
    GeneratedContent generate(ContentBrief brief);
}
