package dev.proofstack.adapter.out.cv;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileDate;
import dev.proofstack.core.port.out.Artifact;

class CvOtherExperienceTest {

    private static final Experience DEV = new Experience("dev", "Developer", "Soft Co", "Madrid",
            Period.ongoingSince(ProfileDate.of(2022)), List.of("Built the API."), List.of("Java"), List.of());
    private static final Experience SHOP = new Experience("shop", "Shop assistant", "Store Co", "",
            new Period(ProfileDate.of(2018), ProfileDate.of(2020)), List.of("Customer service."), List.of(), List.of());

    private static String file(Profile profile, String name) {
        return new CvRenderer().render(profile).stream().filter(a -> a.relativePath().equals(name))
                .findFirst().map(Artifact::content).orElseThrow();
    }

    private static Profile profile(List<Experience> jobs) {
        return new Profile(new PersonalInfo("Ada", "Backend Engineer", "Builds systems", "Spain", null, List.of()),
                jobs, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void jobsWithoutTechnologiesGoToOneLineEntriesWhenOthersHaveThem() {
        String md = file(profile(List.of(DEV, SHOP)), "cv.md");
        assertThat(md.indexOf("## Experience")).isLessThan(md.indexOf("### Developer · Soft Co"));
        assertThat(md).contains("## Other experience\n\n- Shop assistant · Store Co (2018 – 2020): Customer service.\n")
                .doesNotContain("### Shop assistant");
        String html = file(profile(List.of(DEV, SHOP)), "cv.html");
        assertThat(html).contains("<h2>Other experience</h2>").contains("Store Co");
    }

    @Test
    void noSplitWhenNoJobListsTechnologies() {
        String md = file(profile(List.of(SHOP)), "cv.md");
        assertThat(md).contains("## Experience", "### Shop assistant · Store Co").doesNotContain("Other experience");
    }
}
