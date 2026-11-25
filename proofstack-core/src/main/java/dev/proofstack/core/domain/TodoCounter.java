package dev.proofstack.core.domain;

/** Counts placeholder values ({@value #MARKER}) left in a profile, e.g. after an import. */
public final class TodoCounter {

    public static final String MARKER = "TODO";

    /**
     * Start of a highlight saying the entry's period is not real (an importer had no date, or only a year). The schema
     * needs a month, so a placeholder date is stored; outputs that print periods must leave this one out.
     */
    public static final String DATES = MARKER + ": dates";

    private TodoCounter() {
    }

    public static boolean isPlaceholder(String text) {
        return text != null && text.contains(MARKER);
    }

    public static int count(Profile profile) {
        return (int) ProfileTexts.of(profile).stream().filter(t -> isPlaceholder(t.value())).count();
    }
}
