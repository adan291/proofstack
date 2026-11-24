package dev.proofstack.core.domain.content;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;

/** Output formats derived from a single profile entry: "one source, many formats". */
public enum ContentFormat {
    LINKEDIN_POST("linkedin-post", 3000),
    README("readme", 0),
    CV_BULLETS("cv-bullets", 1500),
    VIDEO_SCRIPT("video-script", 6000),
    /** Proposal for the context / action / result of an evidence, to be reviewed and pasted by the author. */
    EVIDENCE_DRAFT("evidence-draft", 1500),
    NOTEBOOKLM_BRIEF("notebooklm-brief", 0);

    private final String slug;
    private final int maxLength;

    ContentFormat(String slug, int maxLength) {
        this.slug = slug;
        this.maxLength = maxLength;
    }

    public String slug() {
        return slug;
    }

    /** Hard limit on the body length, if the target platform has one. */
    public OptionalInt maxLength() {
        return maxLength > 0 ? OptionalInt.of(maxLength) : OptionalInt.empty();
    }

    public static Optional<ContentFormat> fromSlug(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.slug.equals(normalized)).findFirst();
    }

    public static String slugs() {
        return Arrays.stream(values()).map(ContentFormat::slug).collect(Collectors.joining(", "));
    }
}
