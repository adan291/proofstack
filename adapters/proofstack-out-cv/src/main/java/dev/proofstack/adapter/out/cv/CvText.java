package dev.proofstack.adapter.out.cv;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import dev.proofstack.core.domain.Area;

/** Section titles the CV adds around the profile's own text. Unknown languages fall back to English. */
public record CvText(
        String lang, String profile, String skills, String experience, String projects, String education,
        String stack, String result, String technologies, String present,
        Map<Area, String> areas, Locale locale) {

    static CvText forLanguage(String language) {
        String base = language == null ? "en" : language.split("-")[0];
        return "es".equals(base) ? SPANISH : ENGLISH;
    }

    String area(Area area) {
        return areas.get(area);
    }

    private static final CvText ENGLISH = new CvText("en", "Profile", "Skills", "Experience", "Selected projects",
            "Education", "Stack", "Result", "Technologies", "Present", areas(
            "Backend", "Architecture", "Messaging", "Data", "DevOps & observability", "AI",
            "Hardware & automation", "Leadership", "Communication"), Locale.ENGLISH);

    private static final CvText SPANISH = new CvText("es", "Perfil", "Habilidades", "Experiencia",
            "Proyectos destacados", "Formación", "Stack", "Resultado", "Tecnologías", "Actualidad", areas(
            "Backend", "Arquitectura", "Mensajería", "Datos", "DevOps y observabilidad", "IA",
            "Hardware y automatización", "Liderazgo", "Comunicación"), Locale.forLanguageTag("es"));

    /** Labels in {@link Area} declaration order. */
    private static Map<Area, String> areas(String... labels) {
        Area[] all = Area.values();
        if (labels.length != all.length) {
            throw new IllegalStateException("one label per Area expected");
        }
        Map<Area, String> map = new EnumMap<>(Area.class);
        for (int i = 0; i < all.length; i++) {
            map.put(all[i], labels[i]);
        }
        return Map.copyOf(map);
    }
}
