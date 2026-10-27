package dev.proofstack.core.domain.content;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A generated draft. {@code warnings} are not part of the content: they are for the author to review.
 */
public record GeneratedContent(
        ContentFormat format,
        String title,
        String body,
        List<String> hashtags,
        List<String> warnings) {

    public GeneratedContent {
        Objects.requireNonNull(format, "format");
        title = title == null ? "" : title.strip();
        if (body == null || body.isBlank()) {
            throw new ContentGenerationException("generated body is empty", ContentGenerationException.Kind.INVALID_OUTPUT);
        }
        body = body.strip();
        hashtags = hashtags == null ? List.of() : hashtags.stream()
                .map(String::strip)
                .filter(h -> !h.isEmpty())
                .map(h -> h.startsWith("#") ? h : "#" + h)
                .map(h -> h.replaceAll("\\s+", ""))
                .distinct()
                .toList();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public GeneratedContent withWarnings(List<String> newWarnings) {
        return new GeneratedContent(format, title, body, hashtags, newWarnings);
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        if (!title.isEmpty()) {
            md.append("# ").append(title).append("\n\n");
        }
        md.append(body).append('\n');
        if (!hashtags.isEmpty()) {
            md.append('\n').append(hashtags.stream().collect(Collectors.joining(" "))).append('\n');
        }
        return md.toString();
    }
}
