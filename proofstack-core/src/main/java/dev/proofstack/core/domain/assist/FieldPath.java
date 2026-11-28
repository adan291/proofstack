package dev.proofstack.core.domain.assist;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Lab;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A free-text field of the profile, addressed by ids and not by positions
 * ({@code projects[tomcat].areaNotes.backend}), so the same path means the same field in every language file.
 * Only list items of plain strings ({@code learnings[1]}, {@code highlights[0]}) use an index, as they have no id.
 * The path decides the field's purpose and its length limit; anything that is not a text the author writes
 * (names, links, emails) is rejected here.
 */
public record FieldPath(String value, String purposeKey, int maxLength, String id, Integer index, Area areaOrNull) {

    /** Same limits as {@code text} and {@code shortText} in {@code schema/profile.schema.json}. */
    public static final int TEXT_LENGTH = 4000;
    public static final int SHORT_TEXT_LENGTH = 300;

    private static final String ID = "([a-z0-9][a-z0-9-]{0,63})";
    private static final Pattern PERSON = Pattern.compile("person\\.(headline|summary|contactMessage)");
    private static final Pattern PROJECT = Pattern.compile("projects\\[" + ID + "]\\.(summary|problem|solution)");
    private static final Pattern LEARNING = Pattern.compile("projects\\[" + ID + "]\\.learnings\\[(\\d{1,4})]");
    private static final Pattern AREA_NOTE = Pattern.compile("projects\\[" + ID + "]\\.areaNotes\\.([a-z]+)");
    private static final Pattern HIGHLIGHT = Pattern.compile("experience\\[" + ID + "]\\.highlights\\[(\\d{1,4})]");
    private static final Pattern EVIDENCE = Pattern.compile("evidence\\[" + ID + "]\\.(title|context|action|result)");
    private static final Pattern LAB = Pattern.compile("labs\\[" + ID + "]\\.description");

    public static FieldPath parse(String path) {
        String p = path == null ? "" : path;
        Matcher m;
        if ((m = PERSON.matcher(p)).matches()) {
            int max = m.group(1).equals("headline") ? SHORT_TEXT_LENGTH : TEXT_LENGTH;
            return new FieldPath(p, "person." + m.group(1), max, null, null, null);
        }
        if ((m = PROJECT.matcher(p)).matches()) {
            return new FieldPath(p, "projects[]." + m.group(2), TEXT_LENGTH, m.group(1), null, null);
        }
        if ((m = LEARNING.matcher(p)).matches()) {
            return new FieldPath(p, "projects[].learnings[]", TEXT_LENGTH, m.group(1), Integer.valueOf(m.group(2)), null);
        }
        if ((m = AREA_NOTE.matcher(p)).matches()) {
            Area area = areaOf(m.group(2)).orElseThrow(() -> new UnsupportedFieldException("unknown area in '" + p + "'"));
            return new FieldPath(p, "projects[].areaNotes.{area}", SHORT_TEXT_LENGTH, m.group(1), null, area);
        }
        if ((m = HIGHLIGHT.matcher(p)).matches()) {
            return new FieldPath(p, "experience[].highlights[]", TEXT_LENGTH, m.group(1), Integer.valueOf(m.group(2)), null);
        }
        if ((m = EVIDENCE.matcher(p)).matches()) {
            int max = m.group(2).equals("title") ? SHORT_TEXT_LENGTH : TEXT_LENGTH;
            return new FieldPath(p, "evidence[]." + m.group(2), max, m.group(1), null, null);
        }
        if ((m = LAB.matcher(p)).matches()) {
            return new FieldPath(p, "labs[].description", TEXT_LENGTH, m.group(1), null, null);
        }
        throw new UnsupportedFieldException("'" + p + "' is not a text field the assistant can write");
    }

    /** Readable area name for the prompt, when the field is an area note. */
    public Optional<String> area() {
        return Optional.ofNullable(areaOrNull).map(Area::label);
    }

    /** The text of this field in {@code profile}, or empty when the entity or the item does not exist there. */
    public Optional<String> savedText(Profile profile) {
        Matcher m;
        String p = value;
        if ((m = PERSON.matcher(p)).matches()) {
            return Optional.ofNullable(switch (m.group(1)) {
                case "headline" -> profile.person().headline();
                case "summary" -> profile.person().summary();
                default -> profile.person().contactMessage();
            });
        }
        if ((m = PROJECT.matcher(p)).matches()) {
            String field = m.group(2);
            return profile.project(id).map(pr -> switch (field) {
                case "summary" -> pr.summary();
                case "problem" -> pr.problem();
                default -> pr.solution();
            });
        }
        if (LEARNING.matcher(p).matches()) {
            return profile.project(id).flatMap(pr -> item(pr.learnings()));
        }
        if (AREA_NOTE.matcher(p).matches()) {
            return profile.project(id).map(Project::areaNotes).map(notes -> notes.get(areaOrNull));
        }
        if (HIGHLIGHT.matcher(p).matches()) {
            return profile.experience().stream().filter(e -> e.id().equals(id)).findFirst()
                    .map(Experience::highlights).flatMap(this::item);
        }
        if ((m = EVIDENCE.matcher(p)).matches()) {
            String field = m.group(2);
            return profile.evidence(id).map((Function<Evidence, String>) e -> switch (field) {
                case "title" -> e.title();
                case "context" -> e.context();
                case "action" -> e.action();
                default -> e.result();
            });
        }
        return profile.labs().stream().filter(l -> l.id().equals(id)).findFirst().map(Lab::description);
    }

    private Optional<String> item(List<String> items) {
        return index != null && index < items.size() ? Optional.of(items.get(index)) : Optional.empty();
    }

    private static Optional<Area> areaOf(String key) {
        try {
            return Optional.of(Area.valueOf(key.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
