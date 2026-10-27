package dev.proofstack.core.port.in;

import dev.proofstack.core.domain.content.ContentFormat;
import dev.proofstack.core.domain.content.GeneratedContent;

import java.nio.file.Path;

/** Driving port: draft content about one project or evidence of the profile. */
public interface GenerateContentUseCase {

    /**
     * @param language content language; {@code null} uses the profile language
     * @throws dev.proofstack.core.domain.content.ContentGenerationException on unknown subject or generation failure
     */
    GeneratedContent generate(Path profileLocation, String subjectId, ContentFormat format, String language);
}
