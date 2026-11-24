package dev.proofstack.adapter.out.cv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobOfferTest {

    @Test
    void countsNormalisedTermsAndDropsStopWords() {
        JobOffer offer = JobOffer.parse("Buscamos un Ingeniero de Arquitectura. La arquitectura hexagonal es clave.");

        assertThat(offer.terms()).containsEntry("arquitectura", 2).containsEntry("hexagonal", 1)
                .doesNotContainKeys("de", "la", "es", "buscamos", "un");
    }

    @Test
    void ignoresAccentsAndCase() {
        assertThat(JobOffer.parse("Mensajería, MENSAJERIA").terms()).containsEntry("mensajeria", 2);
    }

    @Test
    void keepsTechnicalTokensWhole() {
        JobOffer offer = JobOffer.parse("Experience with C#, C++ and Node.js. Java17 is a plus.");

        assertThat(offer.terms()).containsKeys("c#", "c++", "node.js", "java17");
        assertThat(JobOffer.looksTechnical("node.js")).isTrue();
        assertThat(JobOffer.looksTechnical("backend")).isFalse();
    }

    @Test
    void versionsYearsAndSalariesAreNotSkills() {
        assertThat(JobOffer.parse("Java 17+ desde 2024, 45000 euros, Kafka").terms())
                .containsKeys("java", "kafka").doesNotContainKeys("17+", "2024", "45000");
    }

    @Test
    void rejectsEmptyOrHugePostings() {
        assertThatThrownBy(() -> JobOffer.parse("  ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobOffer.parse("the and of")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JobOffer.parse("java ".repeat(JobOffer.MAX_CHARS)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}