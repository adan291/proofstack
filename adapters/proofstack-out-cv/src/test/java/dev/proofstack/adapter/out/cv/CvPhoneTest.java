package dev.proofstack.adapter.out.cv;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.Artifact;

class CvPhoneTest {

    private static final Profile PROFILE = new Profile(
            new PersonalInfo("Ada", "Backend Engineer", "Builds systems", "León", "ada@example.com", List.of()),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    private static String md(CvOptions options) {
        return new CvRenderer(options).render(PROFILE).stream().filter(a -> a.relativePath().equals("cv.md"))
                .findFirst().map(Artifact::content).orElseThrow();
    }

    @Test
    void phoneGoesAfterTheEmailOnlyWhenGiven() {
        assertThat(md(new CvOptions(null, 5, null, "+34 600 11 22 33")))
                .contains("[ada@example.com](mailto:ada@example.com) · [+34 600 11 22 33](tel:+34600112233)");
        assertThat(md(CvOptions.defaults())).doesNotContain("tel:");
        assertThat(md(new CvOptions(null, 5, null, "  "))).doesNotContain("tel:");
    }

    @Test
    void aPublicPhoneInTheProfileIsUsedUnlessOneIsGivenAtBuildTime() {
        Profile withPhone = new Profile(new PersonalInfo("Ada", "Backend Engineer", "Builds systems", "León",
                "ada@example.com", List.of(), "+34 611 00 00 00", null), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of());
        java.util.function.Function<CvOptions, String> md = options -> new CvRenderer(options).render(withPhone)
                .stream().filter(a -> a.relativePath().equals("cv.md")).findFirst().map(Artifact::content).orElseThrow();

        assertThat(md.apply(CvOptions.defaults())).contains("[+34 611 00 00 00](tel:+34611000000)");
        assertThat(md.apply(new CvOptions(null, 5, null, "+34 600 11 22 33")))
                .contains("tel:+34600112233").doesNotContain("tel:+34611000000");
    }

    @Test
    void rejectsAnythingThatIsNotAPhoneNumber() {
        for (String bad : new String[]{"call me", "12", "600<script>", "javascript:1"}) {
            assertThatThrownBy(() -> new CvOptions(null, 5, null, bad)).as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
