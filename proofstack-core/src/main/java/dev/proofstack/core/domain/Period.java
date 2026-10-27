package dev.proofstack.core.domain;

import java.time.YearMonth;
import java.util.Optional;

/**
 * Month-precision time span. A {@code null} end means "ongoing".
 */
public record Period(YearMonth start, YearMonth end) {

    public Period {
        Checks.requireNonNull(start, "startDate");
        if (end != null && end.isBefore(start)) {
            throw new InvalidValueException("endDate", "must not be before startDate");
        }
    }

    public static Period ongoingSince(YearMonth start) {
        return new Period(start, null);
    }

    public boolean isOngoing() {
        return end == null;
    }

    public Optional<YearMonth> endDate() {
        return Optional.ofNullable(end);
    }
}
