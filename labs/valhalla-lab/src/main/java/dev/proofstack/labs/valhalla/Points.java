package dev.proofstack.labs.valhalla;

/** The same two-int record, with and without identity. Only the {@code value} modifier differs. */
final class Points {

    private Points() {
    }

    /** Identity record: every instance is a separate heap object reached through a pointer. */
    record IdentityPoint(int x, int y) {
    }

    /** Value record (JEP 401): no identity, so the JVM may flatten it into arrays and fields. */
    value record ValuePoint(int x, int y) {
    }

    /**
     * A Proofstack-shaped candidate: month precision dates like {@code Period} in the core domain.
     * Stored as two ints so the comparison stays about layout, not about java.time internals.
     */
    value record ValueYearMonth(int year, int month) {
        ValueYearMonth {
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("month " + month);
            }
        }

        int ordinal() {
            return year * 12 + month - 1;
        }
    }

    record IdentityYearMonth(int year, int month) {
        IdentityYearMonth {
            if (month < 1 || month > 12) {
                throw new IllegalArgumentException("month " + month);
            }
        }

        int ordinal() {
            return year * 12 + month - 1;
        }
    }
}
