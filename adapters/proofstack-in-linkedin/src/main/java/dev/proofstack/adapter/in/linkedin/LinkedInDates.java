package dev.proofstack.adapter.in.linkedin;

import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the date formats found in LinkedIn exports: "Jan 2020", "ene 2020", "2020", "01/2020". */
final class LinkedInDates {

    private static final Map<String, Integer> MONTHS = Map.ofEntries(
            Map.entry("jan", 1), Map.entry("feb", 2), Map.entry("mar", 3), Map.entry("apr", 4),
            Map.entry("may", 5), Map.entry("jun", 6), Map.entry("jul", 7), Map.entry("aug", 8),
            Map.entry("sep", 9), Map.entry("oct", 10), Map.entry("nov", 11), Map.entry("dec", 12),
            // Spanish
            Map.entry("ene", 1), Map.entry("abr", 4), Map.entry("ago", 8), Map.entry("set", 9),
            Map.entry("dic", 12));

    private static final Pattern MONTH_YEAR = Pattern.compile("^(\\p{L}{3,})\\.?\\s+(\\d{4})$");
    private static final Pattern NUMERIC = Pattern.compile("^(\\d{1,2})/(\\d{4})$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");

    private LinkedInDates() {
    }

    /** "2020": {@link #parse} reads it as January, which LinkedIn never said. */
    static boolean isYearOnly(String value) {
        return value != null && YEAR.matcher(value.strip()).matches();
    }

    static Optional<YearMonth> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        Matcher m = MONTH_YEAR.matcher(v);
        if (m.matches()) {
            Integer month = MONTHS.get(m.group(1).substring(0, 3));
            return month == null ? Optional.empty() : Optional.of(YearMonth.of(Integer.parseInt(m.group(2)), month));
        }
        m = NUMERIC.matcher(v);
        if (m.matches()) {
            int month = Integer.parseInt(m.group(1));
            return month >= 1 && month <= 12
                    ? Optional.of(YearMonth.of(Integer.parseInt(m.group(2)), month))
                    : Optional.empty();
        }
        m = YEAR.matcher(v);
        if (m.matches()) {
            return Optional.of(YearMonth.of(Integer.parseInt(m.group(1)), 1));
        }
        return Optional.empty();
    }
}
