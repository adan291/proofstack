package dev.proofstack.core.domain;

import java.util.Locale;
import java.util.Optional;

/**
 * A time span with the precision the user gave: years, or years and months. A {@code null} end means "ongoing".
 */
public record Period(ProfileDate start, ProfileDate end) {

    public Period {
        Checks.requireNonNull(start, "startDate");
        if (end != null && end.isBefore(start)) {
            throw new InvalidValueException("endDate", "must not be before startDate");
        }
    }

    public static Period ongoingSince(ProfileDate start) {
        return new Period(start, null);
    }

    public boolean isOngoing() {
        return end == null;
    }

    public Optional<ProfileDate> endDate() {
        return Optional.ofNullable(end);
    }

    /** "2018 – 2020", "Mar 2021 – Present": the one way every output prints a period. */
    public String format(Locale locale, String present) {
        return start.format(locale) + " – " + endDate().map(e -> e.format(locale)).orElse(present);
    }
}
