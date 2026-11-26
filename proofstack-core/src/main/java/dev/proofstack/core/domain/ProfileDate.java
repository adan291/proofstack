package dev.proofstack.core.domain;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A date as precise as the user knows it: a year ({@code 2018}) or a year and a month ({@code 2018-05}). There is no
 * way to turn a year into a month: outputs print what is known, never an invented January (ADR-015).
 *
 * <p>Ordered by year, then month; a year alone sorts just before the months of that year, so in a newest-first
 * list the entry with a month goes first.
 */
public record ProfileDate(int year, Integer month) implements Comparable<ProfileDate> {

    private static final Pattern TEXT = Pattern.compile("^([0-9]{4})(?:-(0[1-9]|1[0-2]))?$");

    public ProfileDate {
        if (year < 1900 || year > 2100) {
            throw new InvalidValueException("date", "year must be between 1900 and 2100");
        }
        if (month != null && (month < 1 || month > 12)) {
            throw new InvalidValueException("date", "month must be between 1 and 12");
        }
    }

    public static ProfileDate of(int year) {
        return new ProfileDate(year, null);
    }

    public static ProfileDate of(int year, int month) {
        return new ProfileDate(year, month);
    }

    /** {@code 2018} or {@code 2018-05}, the two forms of profile.yaml. */
    public static ProfileDate parse(String text) {
        Matcher m = text == null ? null : TEXT.matcher(text.strip());
        if (m == null || !m.matches()) {
            throw new InvalidValueException("date", "must be a year like 2024 or a year-month like 2024-03");
        }
        int year = Integer.parseInt(m.group(1));
        return m.group(2) == null ? of(year) : of(year, Integer.parseInt(m.group(2)));
    }

    public boolean isYearOnly() {
        return month == null;
    }

    /** "2018", or the month abbreviated in {@code locale}: "May 2018", "may 2018". */
    public String format(Locale locale) {
        return month == null ? Integer.toString(year)
                : DateTimeFormatter.ofPattern("MMM yyyy", locale).format(YearMonth.of(year, month));
    }

    /** Compares the years only when one of the two dates has no month (R1.3 of the year-dates spec). */
    public boolean isBefore(ProfileDate other) {
        return month == null || other.month == null ? year < other.year : compareTo(other) < 0;
    }

    @Override
    public int compareTo(ProfileDate other) {
        if (year != other.year) {
            return Integer.compare(year, other.year);
        }
        return Integer.compare(month == null ? 0 : month, other.month == null ? 0 : other.month);
    }

    @Override
    public String toString() {
        return month == null ? Integer.toString(year) : String.format(Locale.ROOT, "%04d-%02d", year, month);
    }

}
