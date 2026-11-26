package dev.proofstack.adapter.out.cv;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CvLangTest {

    private static int bar(String level) {
        return new Cv.Lang("X", level).bar();
    }

    @Test
    void readsCefrLevelsOnASixStepScale() {
        assertThat(bar("A1")).isEqualTo(1);
        assertThat(bar("A2")).isEqualTo(2);
        assertThat(bar("B1")).isEqualTo(3);
        assertThat(bar("b2 (First)")).isEqualTo(4);
        assertThat(bar("C1")).isEqualTo(5);
        assertThat(bar("C2")).isEqualTo(6);
    }

    @Test
    void readsCommonWordsInSpanishAndEnglish() {
        assertThat(bar("Nativo")).isEqualTo(6);
        assertThat(bar("Native")).isEqualTo(6);
        assertThat(bar("Bilingüe")).isEqualTo(6);
        assertThat(bar("Nociones")).isEqualTo(1);
        assertThat(bar("En aprendizaje")).isEqualTo(1);
        assertThat(bar("Básico")).isEqualTo(1);
    }

    @Test
    void unknownOrMissingLevelHasNoBar() {
        assertThat(bar("")).isZero();
        assertThat(bar("Profesional")).isZero();
        assertThat(new Cv.Lang("X", "").hasBar()).isFalse();
    }
}
