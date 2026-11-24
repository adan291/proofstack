package dev.proofstack.cli;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;

/** Whole-system hexagonal rules (ADR-001). Runs here because this module sees every module. */
class HexagonalArchitectureTest {

    private static JavaClasses all;

    @BeforeAll
    static void importClasses() {
        all = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.proofstack");
        assertThat(all.containPackage("dev.proofstack.core.domain")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.in.yaml")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.out.web")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.out.cv")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.out.llm")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.out.yaml")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.in.linkedin")).isTrue();
        assertThat(all.containPackage("dev.proofstack.adapter.in.github")).isTrue();
    }

    @Test
    void layersRespectHexagonalDirection() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Core").definedBy("dev.proofstack.core..")
                .layer("Adapters").definedBy("dev.proofstack.adapter..")
                .layer("Bootstrap").definedBy("dev.proofstack.cli..")
                .whereLayer("Bootstrap").mayNotBeAccessedByAnyLayer()
                .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Bootstrap")
                .whereLayer("Core").mayOnlyBeAccessedByLayers("Adapters", "Bootstrap")
                .check(all);
    }

    @Test
    void inboundAdaptersDoNotKnowOutboundAdapters() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("dev.proofstack.adapter.out..")
                .check(all);
    }

    @Test
    void outboundAdaptersDoNotKnowInboundAdapters() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter.out..")
                .should().dependOnClassesThat().resideInAPackage("dev.proofstack.adapter.in..")
                .check(all);
    }

    @Test
    void outboundAdaptersAreIndependentOfEachOther() {
        for (String adapter : new String[]{"web", "cv", "llm", "yaml"}) {
            noClasses().that().resideInAPackage("dev.proofstack.adapter.out." + adapter + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            java.util.stream.Stream.of("web", "cv", "llm", "yaml")
                                    .filter(other -> !other.equals(adapter))
                                    .map(other -> "dev.proofstack.adapter.out." + other + "..")
                                    .toArray(String[]::new))
                    .check(all);
        }
    }

    @Test
    void adaptersUseCoreOnlyThroughPortsAndDomain() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter..")
                .should().dependOnClassesThat().resideInAPackage("dev.proofstack.core.application..")
                .check(all);
    }
}
