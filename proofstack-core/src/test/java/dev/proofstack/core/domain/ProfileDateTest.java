package dev.proofstack.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** A date as precise as the user knows it: a year, or a year and a month. Never an invented month. */
class ProfileDateTest {

    @Test
    void readsAndWritesBothPrecisions() {
        assertThat(ProfileDate.parse("2018")).isEqualTo(ProfileDate.of(2018));
        assertThat(ProfileDate.parse("2018-05")).isEqualTo(ProfileDate.of(2018, 5));
        assertThat(ProfileDate.of(2018)).hasToString("2018");
        assertThat(ProfileDate.of(2018, 5)).hasToString("2018-05");
        assertThat(ProfileDate.of(2018).isYearOnly()).isTrue();
        for (String bad : List.of("18", "2018-13", "2018-5", "May 2018", "")) {
            assertThatThrownBy(() -> ProfileDate.parse(bad)).as(bad).isInstanceOf(InvalidValueException.class);
        }
    }

    @Test
    void printsOnlyWhatIsKnown() {
        assertThat(ProfileDate.of(2018).format(Locale.ENGLISH)).isEqualTo("2018");
        assertThat(ProfileDate.of(2018, 5).format(Locale.ENGLISH)).isEqualTo("May 2018");
        assertThat(new Period(ProfileDate.of(2018), ProfileDate.of(2020)).format(Locale.ENGLISH, "Present"))
                .isEqualTo("2018 – 2020");
        assertThat(Period.ongoingSince(ProfileDate.of(2021, 3)).format(Locale.ENGLISH, "Present"))
                .isEqualTo("Mar 2021 – Present");
    }

    /** Newest first; on the same year, the one with a month goes first (R2.2). */
    @Test
    void ordersByYearThenMonthWithTheYearAlone() {
        List<ProfileDate> newestFirst = Stream.of(ProfileDate.of(2018), ProfileDate.of(2019, 1),
                        ProfileDate.of(2018, 5), ProfileDate.of(2017, 12))
                .sorted(java.util.Comparator.reverseOrder()).toList();

        assertThat(newestFirst).containsExactly(ProfileDate.of(2019, 1), ProfileDate.of(2018, 5),
                ProfileDate.of(2018), ProfileDate.of(2017, 12));
    }

    /** With different precisions, only the years can be compared (R1.3). */
    @Test
    void anEndBeforeTheStartIsRejectedOnlyWhenItReallyIs() {
        assertThat(new Period(ProfileDate.of(2018), ProfileDate.of(2018, 5)).end()).isEqualTo(ProfileDate.of(2018, 5));
        assertThat(new Period(ProfileDate.of(2018, 5), ProfileDate.of(2018)).end()).isEqualTo(ProfileDate.of(2018));
        assertThatThrownBy(() -> new Period(ProfileDate.of(2019), ProfileDate.of(2018, 12)))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> new Period(ProfileDate.of(2018, 6), ProfileDate.of(2018, 5)))
                .isInstanceOf(InvalidValueException.class);
    }
}
