package dev.proofstack.core.domain.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GroundingCheckTest {

    private static final String SOURCE = "Migración de Tomcat 9 a Tomcat 10 con Java 25. Equipo de ~20 personas. Ahorro del 15 %.";

    @Test
    void flagsInventedMetrics() {
        assertThat(GroundingCheck.findUnsupportedNumbers("Reduje la latencia un 40% y ahorré 1200 horas.", SOURCE))
                .containsExactly("40%", "1200");
    }

    @Test
    void acceptsNumbersPresentInSource() {
        assertThat(GroundingCheck.findUnsupportedNumbers(
                "Pasé de Tomcat 9 a Tomcat 10 en Java 25 coordinando a 20 personas; ahorro del 15%.", SOURCE))
                .isEmpty();
    }

    @Test
    void ignoresSingleDigitsAndEmbeddedNumbers() {
        assertThat(GroundingCheck.findUnsupportedNumbers("1. Contexto\n2. Solución\nUsé OAuth2 y H2.", SOURCE))
                .isEmpty();
    }

    @Test
    void flagsSingleDigitPercentages() {
        assertThat(GroundingCheck.findUnsupportedNumbers("Mejora del 5%", SOURCE)).containsExactly("5%");
    }

    @Test
    void treatsDecimalCommaAndDotAlike() {
        assertThat(GroundingCheck.findUnsupportedNumbers("Versión 2,5", "Release 2.5")).isEmpty();
    }
}
