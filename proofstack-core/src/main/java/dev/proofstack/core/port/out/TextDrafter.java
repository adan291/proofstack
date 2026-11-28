package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.assist.AssistantBrief;
import dev.proofstack.core.domain.assist.Draft;

/** Driven port: a model that rewrites one text. It only talks to the provider; the core decides everything else. */
public interface TextDrafter {

    /**
     * @throws dev.proofstack.core.domain.content.ContentGenerationException if the provider is unavailable or its
     *                                                                       answer breaks the contract
     */
    Draft draft(AssistantBrief brief);
}
