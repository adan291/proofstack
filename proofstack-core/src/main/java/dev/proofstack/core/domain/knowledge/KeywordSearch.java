package dev.proofstack.core.domain.knowledge;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dependency-free lexical search over chunks. Used where no embedding model is available
 * (e.g. the MCP server, which runs inside the developer's editor).
 *
 * <p>Score = fraction of query terms found in the chunk, plus 0.25 per term found in the title,
 * capped at 1. Accents and case are ignored, so "migracion" matches "Migración".
 */
public final class KeywordSearch {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "what", "which", "has", "have", "does", "about", "his", "her", "who",
            "que", "qué", "con", "del", "los", "las", "una", "por", "para", "sobre", "tiene", "ha", "como", "cual");

    private KeywordSearch() {
    }

    public static List<ScoredChunk> search(List<KnowledgeChunk> chunks, String query, int limit) {
        Set<String> terms = terms(query);
        if (terms.isEmpty() || limit < 1) {
            return List.of();
        }
        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, score(chunk, terms)))
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(limit)
                .toList();
    }

    private static double score(KnowledgeChunk chunk, Set<String> terms) {
        Set<String> body = terms(chunk.title() + " " + chunk.text());
        Set<String> title = terms(chunk.title());
        long inBody = terms.stream().filter(t -> body.stream().anyMatch(w -> w.startsWith(t))).count();
        long inTitle = terms.stream().filter(t -> title.stream().anyMatch(w -> w.startsWith(t))).count();
        return Math.min(1.0, (double) inBody / terms.size() + 0.25 * inTitle);
    }

    static Set<String> terms(String text) {
        String folded = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return Arrays.stream(folded.split("[^a-z0-9+#]+"))
                .filter(w -> w.length() >= 2)
                .filter(w -> !STOPWORDS.contains(w))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
