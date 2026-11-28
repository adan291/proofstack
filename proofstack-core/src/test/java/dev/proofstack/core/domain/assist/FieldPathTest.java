package dev.proofstack.core.domain.assist;

import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Fixtures;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldPathTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "person.headline|person.headline|300",
            "person.summary|person.summary|4000",
            "person.contactMessage|person.contactMessage|4000",
            "projects[tomcat].summary|projects[].summary|4000",
            "projects[tomcat].problem|projects[].problem|4000",
            "projects[tomcat].solution|projects[].solution|4000",
            "projects[tomcat].learnings[1]|projects[].learnings[]|4000",
            "projects[tomcat].areaNotes.backend|projects[].areaNotes.{area}|300",
            "experience[acme].highlights[0]|experience[].highlights[]|4000",
            "evidence[team].title|evidence[].title|300",
            "evidence[team].context|evidence[].context|4000",
            "evidence[team].action|evidence[].action|4000",
            "evidence[team].result|evidence[].result|4000",
            "labs[lab-1].description|labs[].description|4000"})
    void classifiesEveryEditableField(String path, String purposeKey, int maxLength) {
        FieldPath field = FieldPath.parse(path);
        assertThat(field.purposeKey()).isEqualTo(purposeKey);
        assertThat(field.maxLength()).isEqualTo(maxLength);
        assertThat(field.value()).isEqualTo(path);
    }

    @Test
    void exposesTheAreaOfAnAreaNote() {
        assertThat(FieldPath.parse("projects[tomcat].areaNotes.devops").area()).contains("DevOps & Observability");
        assertThat(FieldPath.parse("person.summary").area()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "person.email", "person.phone", "person.name", "projects[x].name", "projects[x].links[0].url",
            "projects[].summary", "projects[x].learnings", "projects[x].areaNotes.nope", "../person.summary",
            "person.summary.extra", "$.person.summary", "projects[A B].summary", "experience[a].highlights[-1]"})
    void rejectsEverythingElse(String path) {
        assertThatThrownBy(() -> FieldPath.parse(path)).isInstanceOf(UnsupportedFieldException.class);
    }

    @Test
    void readsTheSavedTextByIdNotByPosition() {
        Project first = Fixtures.project("a", Area.BACKEND);
        Project second = new Project("b", "B", "Second summary", "Second problem", "Sol", List.of(),
                List.of("one", "two"), List.of(), List.of(Area.BACKEND), List.of(), false,
                Map.of(Area.BACKEND, "Note"));
        Profile profile = new Profile(Fixtures.person(), List.of(), List.of(), List.of(first, second), List.of(),
                List.of(), List.of());
        assertThat(FieldPath.parse("projects[b].summary").savedText(profile)).contains("Second summary");
        assertThat(FieldPath.parse("projects[b].learnings[1]").savedText(profile)).contains("two");
        assertThat(FieldPath.parse("projects[b].areaNotes.backend").savedText(profile)).contains("Note");
        assertThat(FieldPath.parse("projects[a].areaNotes.backend").savedText(profile)).isEmpty();
        assertThat(FieldPath.parse("projects[zzz].summary").savedText(profile)).isEmpty();
        assertThat(FieldPath.parse("projects[b].learnings[9]").savedText(profile)).isEmpty();
        assertThat(FieldPath.parse("person.headline").savedText(profile)).contains("Backend Engineer");
        assertThat(FieldPath.parse("person.contactMessage").savedText(profile)).isEmpty();
    }

    @Test
    void personalInfoFixtureHasNoContactMessage() {
        PersonalInfo person = Fixtures.person();
        assertThat(person.contactMessage()).isNull();
    }
}
