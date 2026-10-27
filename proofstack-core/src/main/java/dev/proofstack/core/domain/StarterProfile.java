package dev.proofstack.core.domain;

import java.util.List;

/**
 * The profile a new user starts from: valid, small, and full of explicit TODOs so that
 * {@code proofstack doctor} refuses to publish it until it has been completed.
 */
public final class StarterProfile {

    private StarterProfile() {
    }

    public static Profile create(String name, String headline, String language) {
        boolean es = language != null && language.startsWith("es");
        String todo = TodoCounter.MARKER + ": ";

        PersonalInfo person = new PersonalInfo(name, headline,
                todo + (es ? "2–3 frases: qué construyes, para quién y qué estás explorando ahora"
                        : "2–3 sentences: what you build, for whom and what you are exploring now"),
                null, null, List.of());

        Evidence evidence = new Evidence("my-first-evidence", EvidenceCategory.TECHNICAL,
                todo + (es ? "una prueba concreta de lo que sabes hacer" : "a concrete proof of what you can do"),
                todo + (es ? "la situación" : "the situation"),
                todo + (es ? "qué hiciste tú" : "what you did"),
                todo + (es ? "qué cambió (sin inventar métricas)" : "what changed (do not invent metrics)"),
                null, List.of(Area.BACKEND));

        Project project = new Project("my-first-project",
                todo + (es ? "nombre del proyecto" : "project name"),
                todo + (es ? "una frase sobre el proyecto" : "one sentence about the project"),
                todo + (es ? "qué problema resolvía" : "which problem it solved"),
                todo + (es ? "cómo lo resolviste" : "how you solved it"),
                List.of("Java"),
                List.of(todo + (es ? "lo que aprendiste" : "what you learned")),
                List.of(), List.of(Area.BACKEND), List.of(evidence.id()));

        return new Profile(es ? "es" : "en", person, List.of(), List.of(), List.of(project),
                List.of(new Skill("Java", Area.BACKEND)), List.of(evidence), List.of());
    }
}
