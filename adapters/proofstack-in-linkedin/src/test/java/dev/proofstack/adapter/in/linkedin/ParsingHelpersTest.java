package dev.proofstack.adapter.in.linkedin;

import dev.proofstack.core.domain.Area;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.ProfileDate;

import static org.assertj.core.api.Assertions.assertThat;

class ParsingHelpersTest {

    @Test
    void csvHandlesQuotesCommasNewlinesAndCrlf() {
        var rows = CsvParser.parse("A,B\r\n\"x, y\",\"line1\r\nline2 \"\"q\"\"\"\r\n,\r\n");

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("a")).isEqualTo("x, y");
        assertThat(rows.getFirst().get("B")).isEqualTo("line1\r\nline2 \"q\"");
        assertThat(rows.getFirst().get("missing")).isEmpty();
    }

    @Test
    void datesInEnglishSpanishNumericAndYearOnly() {
        assertThat(LinkedInDates.parse("Jan 2020")).contains(ProfileDate.of(2020, 1));
        assertThat(LinkedInDates.parse("ene 2020")).contains(ProfileDate.of(2020, 1));
        assertThat(LinkedInDates.parse("ago. 2021")).contains(ProfileDate.of(2021, 8));
        assertThat(LinkedInDates.parse("September 2019")).contains(ProfileDate.of(2019, 9));
        assertThat(LinkedInDates.parse("03/2018")).contains(ProfileDate.of(2018, 3));
        assertThat(LinkedInDates.parse("2017")).contains(ProfileDate.of(2017));
        assertThat(LinkedInDates.parse("")).isEmpty();
        assertThat(LinkedInDates.parse("someday")).isEmpty();
        assertThat(LinkedInDates.parse("13/2018")).isEmpty();
    }

    @Test
    void idsAreSchemaCompliantAndUnique() {
        Ids ids = new Ids();
        assertThat(ids.unique("Telefónica I+D", "x")).isEqualTo("telefonica-i-d");
        assertThat(ids.unique("Telefónica I+D", "x")).isEqualTo("telefonica-i-d-2");
        assertThat(ids.unique("!!!", "project")).isEqualTo("project");
        assertThat(ids.unique("a".repeat(100), "x")).hasSize(64).matches("^[a-z0-9][a-z0-9-]{0,63}$");
    }

    @Test
    void shortKeywordsMatchWholeWordsOnly() {
        assertThat(dev.proofstack.core.domain.AreaClassifier.classify("Generative AI")).isEqualTo(Area.AI);
        assertThat(dev.proofstack.core.domain.AreaClassifier.classify("Maintenance")).isEqualTo(Area.BACKEND);
        assertThat(dev.proofstack.core.domain.AreaClassifier.classify("PostgreSQL")).isEqualTo(Area.DATA);
        assertThat(dev.proofstack.core.domain.AreaClassifier.classify("Arduino")).isEqualTo(Area.HARDWARE);
    }
}
