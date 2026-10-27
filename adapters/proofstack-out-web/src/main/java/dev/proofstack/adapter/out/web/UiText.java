package dev.proofstack.adapter.out.web;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.LabType;

/**
 * Theme UI strings. The profile content is written by the user; these are only the labels
 * the theme adds around it. Unknown languages fall back to English.
 */
public record UiText(
        String skipToContent, String links, String systemTitle, String systemHint, String core,
        String skills, String projects, String evidence, String problem, String solution, String learnings,
        String techStack, String backedBy, String context, String action, String result,
        String experience, String education, String technologies, String builtWith,
        String present, String labLabel,
        Noun projectNoun, Noun evidenceNoun, Noun labNoun, Noun skillNoun,
        Map<Area, String> areas, Map<EvidenceCategory, String> categories, Map<LabType, String> labTypes,
        AskText ask,
        Locale locale) {

    /** Labels of the optional "ask my portfolio" widget. */
    public record AskText(String title, String hint, String label, String button, String thinking,
                          String unknown, String busy, String unavailable, String sources) {
    }

    /** Singular/plural pair. */
    record Noun(String one, String many) {
        String count(int n) {
            return n + " " + (n == 1 ? one : many);
        }
    }

    static UiText forLanguage(String language) {
        String base = language == null ? "en" : language.split("-")[0];
        return "es".equals(base) ? SPANISH : ENGLISH;
    }

    String area(Area area) {
        return areas.get(area);
    }

    String category(EvidenceCategory category) {
        return categories.get(category);
    }

    String labType(LabType type) {
        return labTypes.get(type);
    }

    static final UiText ENGLISH = new UiText(
            "Skip to content", "Links", "System architecture",
            "Each node is an area of my work. Open one to see the projects and evidence behind it.", "core",
            "Skills", "Projects", "Evidence", "Problem.", "Solution.", "Learnings.",
            "Tech stack", "Backed by:", "Context", "Action", "Result",
            "Experience", "Education", "Technologies", "Built with Proofstack · portfolio as code",
            "Present", "lab",
            new Noun("project", "projects"), new Noun("evidence", "evidence"),
            new Noun("lab", "labs"), new Noun("skill", "skills"),
            areas("Backend", "Architecture", "Messaging", "Data", "DevOps & Observability", "AI",
                    "Hardware & Automation", "Leadership", "Communication"),
            categories("Leadership", "Mentoring", "Communication", "Documentation", "Technical"),
            labTypes("Software", "AI", "Hardware", "Automation"),
            new AskText("Ask my portfolio",
                    "Answers come only from this portfolio and cite their sources. If it is not here, it says so.",
                    "Your question", "Ask", "Thinking…",
                    "That is not in my portfolio. Try asking about a technology, a project or a skill.",
                    "Too many questions in a short time. Try again in a minute.",
                    "The assistant is not available right now. Everything above still works.",
                    "Sources:"),
            Locale.ENGLISH);

    static final UiText SPANISH = new UiText(
            "Saltar al contenido", "Enlaces", "Arquitectura del sistema",
            "Cada nodo es un área de mi trabajo. Ábrelo para ver los proyectos y las evidencias que lo respaldan.",
            "core",
            "Skills", "Proyectos", "Evidencias", "Problema.", "Solución.", "Aprendizajes.",
            "Stack", "Respaldado por:", "Contexto", "Acción", "Resultado",
            "Experiencia", "Formación", "Tecnologías", "Hecho con Proofstack · portfolio as code",
            "Actualidad", "lab",
            new Noun("proyecto", "proyectos"), new Noun("evidencia", "evidencias"),
            new Noun("lab", "labs"), new Noun("skill", "skills"),
            areas("Backend", "Arquitectura", "Mensajería", "Datos", "DevOps y observabilidad", "IA",
                    "Hardware y automatización", "Liderazgo", "Comunicación"),
            categories("Liderazgo", "Mentoring", "Comunicación", "Documentación", "Técnica"),
            labTypes("Software", "IA", "Hardware", "Automatización"),
            new AskText("Pregúntale a mi portfolio",
                    "Las respuestas salen solo de este portfolio y citan sus fuentes. Si algo no está aquí, lo dice.",
                    "Tu pregunta", "Preguntar", "Pensando…",
                    "Eso no está en mi portfolio. Prueba a preguntar por una tecnología, un proyecto o una competencia.",
                    "Demasiadas preguntas en poco tiempo. Prueba de nuevo en un minuto.",
                    "El asistente no está disponible ahora mismo. El resto de la página funciona igual.",
                    "Fuentes:"),
            Locale.of("es", "ES"));

    private static Map<Area, String> areas(String... labels) {
        Map<Area, String> map = new EnumMap<>(Area.class);
        Area[] values = Area.values();
        for (int i = 0; i < values.length; i++) {
            map.put(values[i], labels[i]);
        }
        return Map.copyOf(map);
    }

    private static Map<EvidenceCategory, String> categories(String... labels) {
        Map<EvidenceCategory, String> map = new EnumMap<>(EvidenceCategory.class);
        EvidenceCategory[] values = EvidenceCategory.values();
        for (int i = 0; i < values.length; i++) {
            map.put(values[i], labels[i]);
        }
        return Map.copyOf(map);
    }

    private static Map<LabType, String> labTypes(String... labels) {
        Map<LabType, String> map = new EnumMap<>(LabType.class);
        LabType[] values = LabType.values();
        for (int i = 0; i < values.length; i++) {
            map.put(values[i], labels[i]);
        }
        return Map.copyOf(map);
    }
}
