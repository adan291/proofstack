package dev.proofstack.core.application;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.content.ContentBrief;
import dev.proofstack.core.domain.content.ContentFormat;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.ContentGenerationException.Kind;
import dev.proofstack.core.domain.content.ContentSubject;
import dev.proofstack.core.domain.content.GeneratedContent;
import dev.proofstack.core.domain.content.GroundingCheck;
import dev.proofstack.core.port.in.GenerateContentUseCase;
import dev.proofstack.core.port.out.ContentGenerator;
import dev.proofstack.core.port.out.ProfileSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates content generation and applies the honesty rules (ADR-006):
 * platform limits are enforced and invented numbers are flagged.
 */
public final class ContentService implements GenerateContentUseCase {

    private final ProfileSource source;
    private final ContentGenerator generator;

    public ContentService(ProfileSource source, ContentGenerator generator) {
        this.source = Objects.requireNonNull(source, "source");
        this.generator = Objects.requireNonNull(generator, "generator");
    }

    @Override
    public GeneratedContent generate(Path profileLocation, String subjectId, ContentFormat format, String language) {
        Objects.requireNonNull(format, "format");
        Profile profile = source.load(profileLocation);
        ContentSubject subject = ContentSubject.resolve(profile, subjectId).orElseThrow(() ->
                new ContentGenerationException("unknown subject '" + subjectId + "'. Available: "
                        + String.join(", ", ContentSubject.availableIds(profile)), Kind.UNKNOWN_SUBJECT));

        ContentBrief brief = ContentBrief.of(profile, subject, format, language);
        GeneratedContent content = generator.generate(brief);

        format.maxLength().ifPresent(max -> {
            if (content.body().length() > max) {
                throw new ContentGenerationException("generated " + format.slug() + " has " + content.body().length()
                        + " characters; the limit is " + max, Kind.INVALID_OUTPUT);
            }
        });

        List<String> warnings = new ArrayList<>(content.warnings());
        String generatedText = content.title() + "\n" + content.body();
        for (String number : GroundingCheck.findUnsupportedNumbers(generatedText, brief.sourceText())) {
            warnings.add("'" + number + "' does not appear in your profile: verify it or remove it");
        }
        return content.withWarnings(warnings);
    }
}
