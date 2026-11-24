package dev.proofstack.adapter.out.cv;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words of a job posting, counted. Deterministic keyword matching, not a model: it can rank what the profile
 * already proves, but it can never add something the profile does not say.
 *
 * @param terms normalised term (lowercase, no accents) to how many times the posting uses it
 */
public record JobOffer(Map<String, Integer> terms) {

    /** A posting longer than this is not a posting; refuse it instead of silently truncating. */
    public static final int MAX_CHARS = 100_000;

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#.]*");

    private static final Set<String> STOP = Set.of(
            // español
            "de", "la", "el", "en", "y", "a", "los", "las", "un", "una", "unos", "unas", "del", "al", "con", "para",
            "por", "que", "se", "su", "sus", "es", "son", "como", "o", "u", "e", "lo", "le", "les", "no", "mas",
            "sobre", "entre", "desde", "hasta", "tambien", "ser", "estar", "tener", "hay", "esta", "este", "esto",
            "estos", "estas", "ese", "esa", "nuestro", "nuestra", "nuestros", "tu", "tus", "te", "si", "sin", "muy",
            "ya", "pero", "cada", "otros", "otras", "otro", "otra", "tipo", "anos", "experiencia", "equipo", "trabajo",
            "empresa", "puesto", "oferta", "buscamos", "ofrecemos", "requisitos", "valorable", "valorables",
            "conocimientos", "capacidad", "persona", "personas", "incorporacion", "funciones", "perfil", "nivel",
            "formacion", "dia", "dias", "salario", "jornada", "contrato", "ubicacion", "tareas", "responsabilidades", "habilidades",
            // english
            "the", "and", "of", "to", "in", "for", "with", "on", "an", "is", "are", "be", "as", "or", "we", "you",
            "our", "your", "will", "can", "at", "by", "from", "that", "this", "it", "years", "team", "work", "working",
            "role", "job", "skills", "ability", "strong", "knowledge", "requirements", "required", "plus", "must",
            "have", "has", "experience", "good", "about", "who", "what", "all", "any", "more", "other", "such");

    public JobOffer {
        terms = Map.copyOf(terms);
    }

    public static JobOffer parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("the job posting is empty");
        }
        if (text.length() > MAX_CHARS) {
            throw new IllegalArgumentException("the job posting is longer than " + MAX_CHARS + " characters");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        tokens(text).forEach(token -> counts.merge(token, 1, Integer::sum));
        if (counts.isEmpty()) {
            throw new IllegalArgumentException("the job posting has no usable words");
        }
        return new JobOffer(counts);
    }

    /** Normalised, stop-word-free tokens. Keeps {@code c#}, {@code c++} and {@code node.js} whole. */
    static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text);
        while (matcher.find()) {
            String token = normalize(matcher.group().replaceAll("\\.+$", ""));
            // A token that starts with a digit is a version, a salary or a year ("17+", "2024"), never a skill.
            if (token.length() >= 2 && !STOP.contains(token) && Character.isLetter(token.charAt(0))) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalize(String raw) {
        return Normalizer.normalize(raw.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** True for terms that are technical on their face (c#, node.js, java17), so a single mention counts. */
    static boolean looksTechnical(String term) {
        return term.matches(".*[#+].*") || term.matches(".*\\d.*") || term.contains(".");
    }
}
