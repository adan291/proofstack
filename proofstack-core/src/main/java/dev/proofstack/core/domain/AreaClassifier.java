package dev.proofstack.core.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Keyword based area guess for imported data (LinkedIn skills, GitHub languages and topics).
 * Deterministic on purpose: the user reviews the result, and a wrong guess is a one-word edit in the YAML.
 */
public final class AreaClassifier {

    // Order matters: first match wins.
    private static final Map<Area, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put(Area.AI, List.of("machine learning", "machine-learning", "deep learning", "llm", "rag",
                "embedding", "nlp", "inteligencia artificial", "artificial intelligence", "openai", "gemini", "prompt",
                "tensorflow", "pytorch", "langchain", "ollama", "spring-ai", "mcp", "ai"));
        KEYWORDS.put(Area.MESSAGING, List.of("kafka", "rabbitmq", "activemq", "jms", "mqtt", "amqp", "pub/sub",
                "event-driven", "event driven"));
        KEYWORDS.put(Area.DATA, List.of("sql", "postgres", "mysql", "oracle", "mongodb", "redis", "elasticsearch",
                "jpa", "hibernate", "database", "base de datos", "etl", "data"));
        KEYWORDS.put(Area.DEVOPS, List.of("docker", "kubernetes", "k8s", "jenkins", "ci/cd", "github actions",
                "github-actions", "terraform", "aws", "azure", "gcp", "prometheus", "grafana", "observab", "linux",
                "devops", "helm", "dockerfile", "shell"));
        KEYWORDS.put(Area.ARCHITECTURE, List.of("architecture", "arquitectura", "microservic", "hexagonal", "ddd",
                "domain-driven", "clean code", "clean-architecture", "design patterns", "patrones", "solid", "uml"));
        KEYWORDS.put(Area.HARDWARE, List.of("arduino", "esp32", "raspberry", "iot", "embedded", "electr", "plc",
                "c++"));
        KEYWORDS.put(Area.LEADERSHIP, List.of("leadership", "liderazgo", "team management", "gestión de equipos",
                "mentoring", "scrum master", "project management", "gestión de proyectos", "coordina"));
        KEYWORDS.put(Area.COMMUNICATION, List.of("communication", "comunicación", "public speaking", "writing",
                "documentation", "documentación", "presentation", "oratoria"));
    }

    private AreaClassifier() {
    }

    /** Best guess, {@link Area#BACKEND} when nothing matches. */
    public static Area classify(String text) {
        return match(text).orElse(Area.BACKEND);
    }

    /** The matching area, or empty when no keyword matches. */
    public static Optional<Area> match(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String s = " " + text.toLowerCase(Locale.ROOT) + " ";
        for (Map.Entry<Area, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                // Very short keywords ("ai", "rag", "sql") must be whole words to avoid "maintenance" → AI.
                boolean matches = keyword.length() <= 3
                        ? s.matches("(?s).*[^\\p{L}]" + Pattern.quote(keyword) + "[^\\p{L}].*")
                        : s.contains(keyword);
                if (matches) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }
}
