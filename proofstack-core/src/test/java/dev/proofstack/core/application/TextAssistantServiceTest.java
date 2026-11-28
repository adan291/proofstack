package dev.proofstack.core.application;

import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.assist.AssistMode;
import dev.proofstack.core.domain.assist.AssistantBrief;
import dev.proofstack.core.domain.assist.Draft;
import dev.proofstack.core.domain.assist.FieldPath;
import dev.proofstack.core.domain.assist.Suggestion;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import dev.proofstack.core.port.out.TextDrafter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextAssistantServiceTest {

    private static final String PHONE = "+34 600 111 222";
    private static final String EMAIL = "ada@example.com";

    private final Project project = new Project("tomcat", "Tomcat", "Migrated Tomcat 9 to 10 in 2024", "Problem",
            "Solution", List.of("Java"), List.of(), List.of(), List.of(Area.BACKEND), List.of());
    private final Profile profile = new Profile("es",
            new PersonalInfo("Ada Lovelace", "Backend Engineer", "Summary", "London", EMAIL, List.of(), PHONE, "Msg"),
            List.of(), List.of(), List.of(project), List.of(), List.of(), List.of());
    private final AtomicReference<AssistantBrief> received = new AtomicReference<>();

    /** Not a real codec: dumps every text of the profile it is given, which is all the tests need to look at. */
    private final ProfileJsonCodec codec = new ProfileJsonCodec() {
        @Override
        public Profile parse(String json) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String toJson(Profile p) {
            return "{" + p.person() + "|" + p.projects() + "}";
        }
    };

    private TextAssistantService service(Draft draft) {
        TextDrafter drafter = brief -> {
            received.set(brief);
            return draft;
        };
        return new TextAssistantService(drafter, codec);
    }

    private static Draft draft(String text) {
        return new Draft(text, List.of("shortened"), List.of("How many users?"));
    }

    @Test
    void contextNeverContainsPhoneOrEmail() {
        service(draft("Migrated Tomcat.")).suggest(profile, FieldPath.parse("projects[tomcat].summary"),
                "Migrated Tomcat 9 to 10", "", AssistMode.IMPROVE, Locale.ENGLISH);
        assertThat(received.get().profileJson()).doesNotContain(PHONE, EMAIL, "600 111").contains("Ada Lovelace");
    }

    @Test
    void briefCarriesPurposeAreaAndProfileLanguage() {
        service(draft("Note")).suggest(profile, FieldPath.parse("projects[tomcat].areaNotes.devops"), "x",
                "shorter", AssistMode.IMPROVE, Locale.ENGLISH);
        AssistantBrief brief = received.get();
        assertThat(brief.purposeKey()).isEqualTo("projects[].areaNotes.{area}");
        assertThat(brief.area()).isEqualTo("DevOps & Observability");
        assertThat(brief.language()).isEqualTo("es");
        assertThat(brief.request()).isEqualTo("shorter");
        assertThat(brief.maxLength()).isEqualTo(300);
        assertThat(brief.mode()).isEqualTo(AssistMode.IMPROVE);
    }

    @Test
    void translateWritesInTheTargetLanguage() {
        service(draft("Hello")).suggest(profile, FieldPath.parse("person.summary"), "Hola", "", AssistMode.TRANSLATE,
                Locale.ENGLISH);
        assertThat(received.get().language()).isEqualTo("en");
        assertThat(received.get().targetLanguage()).isEqualTo("en");
    }

    @Test
    void jobOfferPassesTheOfferApartFromTheRequest() {
        service(draft("Text")).suggest(profile, FieldPath.parse("person.summary"), "Summary", "We need Kafka",
                AssistMode.JOB_OFFER, Locale.ENGLISH);
        assertThat(received.get().jobOffer()).isEqualTo("We need Kafka");
        assertThat(received.get().request()).isEmpty();
    }

    @Test
    void flagsNumbersThatAreNeitherInTheProfileNorInTheCurrentText() {
        Suggestion s = service(draft("Migrated Tomcat 9 to 10 for 2024 and cut costs 40%, serving 1200 users."))
                .suggest(profile, FieldPath.parse("projects[tomcat].summary"), "Migrated Tomcat 9 to 10 in 2024", "",
                        AssistMode.IMPROVE, Locale.ENGLISH);
        assertThat(s.unsupportedNumbers()).containsExactly("40%", "1200");
    }

    @Test
    void aNumberOfTheUnsavedDraftIsSupported() {
        Suggestion s = service(draft("Served 750 users."))
                .suggest(profile, FieldPath.parse("projects[tomcat].summary"), "Draft with 750 users", "",
                        AssistMode.IMPROVE, Locale.ENGLISH);
        assertThat(s.unsupportedNumbers()).isEmpty();
    }

    @Test
    void aPhoneNumberTheModelRepeatsIsNotSupported() {
        Suggestion s = service(draft("Call me at 600111222 now"))
                .suggest(profile, FieldPath.parse("person.contactMessage"), "Msg", "", AssistMode.IMPROVE, Locale.ENGLISH);
        assertThat(s.unsupportedNumbers()).contains("600111222");
    }

    @Test
    void trimsTextAndListsToTheLimits() {
        Draft long300 = new Draft("x".repeat(500), List.of("1", "2", "3", "4", "5", "6", "7"), List.of("a", "b", "c", "d"));
        Suggestion s = service(long300).suggest(profile, FieldPath.parse("person.headline"), "Backend", "",
                AssistMode.IMPROVE, Locale.ENGLISH);
        assertThat(s.text()).hasSize(300);
        assertThat(s.changes()).hasSize(5);
        assertThat(s.questions()).hasSize(3);
    }

    @Test
    void rejectsAnEmptyProposal() {
        assertThatThrownBy(() -> service(new Draft("  ", List.of(), List.of())).suggest(profile,
                FieldPath.parse("person.summary"), "Summary", "", AssistMode.IMPROVE, Locale.ENGLISH))
                .isInstanceOf(ContentGenerationException.class)
                .extracting(e -> ((ContentGenerationException) e).kind())
                .isEqualTo(ContentGenerationException.Kind.INVALID_OUTPUT);
    }

    @Test
    void enforcesInputLimitsAndModeRequirements() {
        TextAssistantService s = service(draft("ok"));
        FieldPath summary = FieldPath.parse("person.summary");
        assertThatThrownBy(() -> s.suggest(profile, summary, "x".repeat(4001), "", AssistMode.IMPROVE, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("4000");
        assertThatThrownBy(() -> s.suggest(profile, summary, "x", "y".repeat(2001), AssistMode.IMPROVE, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("2000");
        assertThatThrownBy(() -> s.suggest(profile, summary, "", "", AssistMode.TRANSLATE, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.suggest(profile, summary, "x", " ", AssistMode.JOB_OFFER, Locale.ENGLISH))
                .isInstanceOf(IllegalArgumentException.class);
    }

}
