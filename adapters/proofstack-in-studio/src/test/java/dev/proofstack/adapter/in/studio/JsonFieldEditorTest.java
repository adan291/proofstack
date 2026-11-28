package dev.proofstack.adapter.in.studio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import dev.proofstack.core.domain.assist.FieldPath;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class JsonFieldEditorTest {

    private final JsonMapper json = JsonMapper.builder().build();

    private ObjectNode doc() {
        return (ObjectNode) json.readTree("""
                {"person":{"name":"Ada","headline":"Dev","summary":"old"},
                 "projects":[{"id":"a","summary":"A"},{"id":"b","summary":"B","learnings":["one","two"],"areaNotes":{"backend":"n"}},
                             {"id":"c","summary":"C"}],
                 "experience":[{"id":"acme","highlights":["h0","h1"]}],
                 "evidence":[{"id":"team","title":"T","context":"c","action":"a","result":"r"}],
                 "labs":[{"id":"ai","description":"d"}]}""");
    }

    private void set(ObjectNode doc, String path, String text) {
        JsonFieldEditor.set(doc, FieldPath.parse(path), text);
    }

    @Test
    void writesByIdNotByPosition() {
        ObjectNode d = doc();
        set(d, "projects[b].summary", "New B");
        set(d, "projects[c].problem", "Created");
        assertThat(d.at("/projects/0/summary").asString()).isEqualTo("A");
        assertThat(d.at("/projects/1/summary").asString()).isEqualTo("New B");
        assertThat(d.at("/projects/2/problem").asString()).isEqualTo("Created");
    }

    @Test
    void writesEveryKindOfField() {
        ObjectNode d = doc();
        set(d, "person.summary", "S");
        set(d, "person.contactMessage", "M");
        set(d, "projects[b].learnings[1]", "L");
        set(d, "projects[b].areaNotes.backend", "N");
        set(d, "projects[a].areaNotes.devops", "created note");
        set(d, "experience[acme].highlights[0]", "H");
        set(d, "evidence[team].result", "R");
        set(d, "labs[ai].description", "D");
        assertThat(d.at("/person/summary").asString()).isEqualTo("S");
        assertThat(d.at("/person/contactMessage").asString()).isEqualTo("M");
        assertThat(d.at("/projects/1/learnings/1").asString()).isEqualTo("L");
        assertThat(d.at("/projects/1/learnings/0").asString()).isEqualTo("one");
        assertThat(d.at("/projects/1/areaNotes/backend").asString()).isEqualTo("N");
        assertThat(d.at("/projects/0/areaNotes/devops").asString()).isEqualTo("created note");
        assertThat(d.at("/experience/0/highlights/0").asString()).isEqualTo("H");
        assertThat(d.at("/evidence/0/result").asString()).isEqualTo("R");
        assertThat(d.at("/labs/0/description").asString()).isEqualTo("D");
    }

    @Test
    void refusesWhatDoesNotExistInTheOtherDocument() {
        ObjectNode d = doc();
        assertThatThrownBy(() -> set(d, "projects[zzz].summary", "x")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("projects[zzz]");
        assertThatThrownBy(() -> set(d, "projects[b].learnings[5]", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(d, "projects[a].learnings[0]", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> set(d, "experience[nope].highlights[0]", "x")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsTheSameWay() {
        ObjectNode d = doc();
        assertThat(JsonFieldEditor.read(d, FieldPath.parse("projects[b].learnings[1]"))).contains("two");
        assertThat(JsonFieldEditor.read(d, FieldPath.parse("projects[a].problem"))).isEmpty();
        assertThat(JsonFieldEditor.read(d, FieldPath.parse("projects[zzz].summary"))).isEmpty();
    }
}
