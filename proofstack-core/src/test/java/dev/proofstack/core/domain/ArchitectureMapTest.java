package dev.proofstack.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.proofstack.core.domain.Fixtures.*;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureMapTest {

    @Test
    void onlyAreasWithContentAppearInEnumOrder() {
        Profile profile = profile(
                List.of(project("p1", Area.AI)),
                List.of(evidence("e1", EvidenceCategory.LEADERSHIP)),
                List.of(),
                List.of(new Skill("Java", Area.BACKEND)));

        ArchitectureMap map = ArchitectureMap.from(profile);

        assertThat(map.nodes()).extracting(AreaNode::area)
                .containsExactly(Area.BACKEND, Area.AI, Area.LEADERSHIP);
    }

    @Test
    void labProjectsAreAttachedToTheLabArea() {
        Project arduino = project("coffee");
        Lab lab = new Lab("hw", "Hardware Lab", LabType.AUTOMATION, "ESP32 experiments", List.of("coffee"));

        ArchitectureMap map = ArchitectureMap.from(profile(List.of(arduino), List.of(), List.of(lab), List.of()));

        AreaNode hardware = map.nodes().getFirst();
        assertThat(hardware.area()).isEqualTo(Area.HARDWARE);
        assertThat(hardware.projects()).containsExactly(arduino);
        assertThat(hardware.labs()).containsExactly(lab);
    }

    @Test
    void projectIsNotDuplicatedWhenDeclaredAndInLab() {
        Project rag = project("rag", Area.AI);
        Lab lab = new Lab("ai", "AI Lab", LabType.AI, "LLM experiments", List.of("rag"));

        ArchitectureMap map = ArchitectureMap.from(profile(List.of(rag), List.of(), List.of(lab), List.of()));

        assertThat(map.nodes().getFirst().projects()).containsExactly(rag);
    }

    @Test
    void emptyProfileProducesEmptyMap() {
        assertThat(ArchitectureMap.from(profile(List.of(), List.of(), List.of(), List.of())).nodes()).isEmpty();
    }
}
