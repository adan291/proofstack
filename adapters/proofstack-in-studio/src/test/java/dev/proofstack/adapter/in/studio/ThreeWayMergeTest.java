package dev.proofstack.adapter.in.studio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base, mine, theirs: what changed on one side only is taken; what changed on both is a conflict (spec R1). */
class ThreeWayMergeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Set<String> ID_LISTS = Set.of("projects", "experience");

    private static JsonNode j(String json) {
        return JSON.readTree(json.replace('\'', '"'));
    }

    private static ThreeWayMerge.Result merge(String base, String mine, String theirs) {
        return ThreeWayMerge.merge(j(base), j(mine), j(theirs), ID_LISTS, Map.of());
    }

    /** Review H2: two items with the same id used to collapse into one, and the merge dropped the other silently. */
    @Test
    void aListWithARepeatedIdIsOneConflictNotASilentLoss() {
        var r = merge("{'projects':[{'id':'a','name':'A'}]}",
                "{'projects':[{'id':'a','name':'A'},{'id':'p','name':'Proofstack'},{'id':'p','name':'Pedidos'}]}",
                "{'projects':[{'id':'a','name':'A2'}]}");

        assertThat(r.conflicts()).singleElement().satisfies(c -> assertThat(c.path()).isEqualTo("$.projects"));
        assertThat(r.merged().get("projects").size()).as("mine is kept until the user chooses").isEqualTo(3);
    }

    @Test
    void aListWithEmptyIdsIsOneConflictToo() {
        var r = merge("{'projects':[]}",
                "{'projects':[{'id':'','name':'Proofstack'},{'id':'','name':'Pedidos'}]}",
                "{'projects':[{'id':'b','name':'B'}]}");

        assertThat(r.conflicts()).singleElement().satisfies(c -> assertThat(c.path()).isEqualTo("$.projects"));
        assertThat(r.merged().get("projects").size()).isEqualTo(2);
    }

    @Test
    void aChangeOnOneSideIsTaken() {
        var r = merge("{'person':{'name':'Ada','headline':'Dev'}}",
                "{'person':{'name':'Ada','headline':'Staff'}}",
                "{'person':{'name':'Grace','headline':'Dev'}}");

        assertThat(r.merged()).isEqualTo(j("{'person':{'name':'Grace','headline':'Staff'}}"));
        assertThat(r.conflicts()).isEmpty();
    }

    @Test
    void theSameChangeOnBothSidesIsNoConflict() {
        var r = merge("{'a':1}", "{'a':2}", "{'a':2}");

        assertThat(r.merged()).isEqualTo(j("{'a':2}"));
        assertThat(r.conflicts()).isEmpty();
    }

    @Test
    void differentChangesToTheSameFieldAreAConflictThatKeepsMineUntilChosen() {
        var r = merge("{'person':{'name':'Ada'}}", "{'person':{'name':'Ada L.'}}", "{'person':{'name':'Grace'}}");

        assertThat(r.merged()).isEqualTo(j("{'person':{'name':'Ada L.'}}"));
        assertThat(r.conflicts()).singleElement().satisfies(c -> {
            assertThat(c.path()).isEqualTo("$.person.name");
            assertThat(c.mine()).isEqualTo(j("'Ada L.'"));
            assertThat(c.theirs()).isEqualTo(j("'Grace'"));
        });

        var chosen = ThreeWayMerge.merge(j("{'person':{'name':'Ada'}}"), j("{'person':{'name':'Ada L.'}}"),
                j("{'person':{'name':'Grace'}}"), ID_LISTS, Map.of("$.person.name", ThreeWayMerge.Side.THEIRS));
        assertThat(chosen.merged()).isEqualTo(j("{'person':{'name':'Grace'}}"));
        assertThat(chosen.conflicts()).isEmpty();
    }

    @Test
    void fieldsAddedOrRemovedOnOneSideAreTaken() {
        var r = merge("{'a':1,'b':2}", "{'a':1,'b':2,'c':3}", "{'a':1}");

        assertThat(r.merged()).isEqualTo(j("{'a':1,'c':3}"));
        assertThat(r.conflicts()).isEmpty();
    }

    /** Lists with ids are matched by id: reordering, adding and editing different items never conflict. */
    @Test
    void listItemsWithIdsAreMatchedByIdNotByPosition() {
        var r = merge(
                "{'projects':[{'id':'a','name':'A'},{'id':'b','name':'B'}]}",
                "{'projects':[{'id':'b','name':'B'},{'id':'a','name':'A2'},{'id':'m','name':'Mine'}]}",
                "{'projects':[{'id':'a','name':'A'},{'id':'b','name':'B2'},{'id':'t','name':'Theirs'}]}");

        assertThat(r.merged()).isEqualTo(j(
                "{'projects':[{'id':'b','name':'B2'},{'id':'a','name':'A2'},{'id':'m','name':'Mine'},{'id':'t','name':'Theirs'}]}"));
        assertThat(r.conflicts()).isEmpty();
    }

    @Test
    void anItemDeletedOnOneSideAndUntouchedOnTheOtherIsDeleted() {
        var r = merge("{'projects':[{'id':'a','name':'A'},{'id':'b','name':'B'}]}",
                "{'projects':[{'id':'b','name':'B'}]}",
                "{'projects':[{'id':'a','name':'A'},{'id':'b','name':'B'}]}");

        assertThat(r.merged()).isEqualTo(j("{'projects':[{'id':'b','name':'B'}]}"));
        assertThat(r.conflicts()).isEmpty();
    }

    @Test
    void anItemEditedOnOneSideAndDeletedOnTheOtherIsAConflict() {
        var r = merge("{'projects':[{'id':'a','name':'A'}]}", "{'projects':[{'id':'a','name':'A2'}]}",
                "{'projects':[]}");

        assertThat(r.conflicts()).singleElement().satisfies(c -> {
            assertThat(c.path()).isEqualTo("$.projects[id=a]");
            assertThat(c.mine()).isEqualTo(j("{'id':'a','name':'A2'}"));
            assertThat(c.theirs()).isNull();
        });
    }

    @Test
    void sameIdItemsMergeFieldByField() {
        var r = merge("{'projects':[{'id':'a','name':'A','summary':'S'}]}",
                "{'projects':[{'id':'a','name':'A2','summary':'S'}]}",
                "{'projects':[{'id':'a','name':'A','summary':'S2'}]}");

        assertThat(r.merged()).isEqualTo(j("{'projects':[{'id':'a','name':'A2','summary':'S2'}]}"));
        assertThat(r.conflicts()).isEmpty();
    }

    /** Lists without ids (skills, highlights) are one value: changed on both sides, they conflict as a whole. */
    @Test
    void listsWithoutIdsAreComparedWhole() {
        var r = merge("{'skills':[{'name':'Java'}]}", "{'skills':[{'name':'Java'},{'name':'Go'}]}",
                "{'skills':[{'name':'Java'},{'name':'Rust'}]}");

        assertThat(r.conflicts()).singleElement().satisfies(c -> assertThat(c.path()).isEqualTo("$.skills"));
        assertThat(r.merged()).isEqualTo(j("{'skills':[{'name':'Java'},{'name':'Go'}]}"));
    }

    @Test
    void aTypeChangeIsAConflictNotACrash() {
        var r = merge("{'startDate':'2018'}", "{'startDate':2018}", "{'startDate':{'year':2018}}");

        assertThat(r.conflicts()).singleElement().satisfies(c -> assertThat(c.path()).isEqualTo("$.startDate"));
    }
}
