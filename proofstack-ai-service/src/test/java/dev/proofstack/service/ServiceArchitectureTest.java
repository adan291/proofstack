package dev.proofstack.service;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** ADR-001 also holds inside the Spring service: Spring stays in adapters and the composition root. */
class ServiceArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.proofstack");
        assertThat(classes.containPackage("dev.proofstack.adapter.out.springai")).isTrue();
        assertThat(classes.containPackage("dev.proofstack.adapter.in.rest")).isTrue();
    }

    @Test
    void coreNeverSeesSpring() {
        noClasses().that().resideInAPackage("dev.proofstack.core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "dev.proofstack.adapter..",
                        "dev.proofstack.service..")
                .check(classes);
    }

    @Test
    void restAdapterDoesNotKnowSpringAiAdapter() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter.in.rest..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.proofstack.adapter.out..", "org.springframework.ai..")
                .check(classes);
    }

    @Test
    void springAiAdapterDoesNotKnowWeb() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter.out.springai..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.proofstack.adapter.in..",
                        "org.springframework.web..", "jakarta.servlet..")
                .check(classes);
    }

    @Test
    void restAdapterUsesUseCasesNotApplicationServices() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter..")
                .should().dependOnClassesThat().resideInAPackage("dev.proofstack.core.application..")
                .check(classes);
    }
}
