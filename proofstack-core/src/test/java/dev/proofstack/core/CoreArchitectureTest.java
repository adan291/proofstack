package dev.proofstack.core;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces the hexagonal rules inside the core (ADR-001). */
class CoreArchitectureTest {

    private static JavaClasses core;

    @BeforeAll
    static void importClasses() {
        core = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.proofstack.core");
        // Guard against a silently empty import (e.g. unsupported class file version).
        assertThat(core).isNotEmpty();
    }

    @Test
    void coreDependsOnlyOnJava() {
        classes()
                .should().onlyDependOnClassesThat().resideInAnyPackage("dev.proofstack.core..", "java..")
                .because("the core must stay framework-free")
                .check(core);
    }

    @Test
    void domainDoesNotKnowPortsOrApplication() {
        noClasses()
                .that().resideInAPackage("..core.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..core.port..", "..core.application..")
                .check(core);
    }

    @Test
    void portsDoNotKnowApplication() {
        noClasses()
                .that().resideInAPackage("..core.port..")
                .should().dependOnClassesThat().resideInAPackage("..core.application..")
                .check(core);
    }
}
