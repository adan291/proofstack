package dev.proofstack.service.mcp;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** The MCP adapter is read-only by construction: it cannot reach any writer, network or file API. */
class McpArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void load() {
        classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.proofstack");
        assertThat(classes.containPackage("dev.proofstack.adapter.in.mcp")).isTrue();
    }

    @Test
    void mcpToolsCannotWriteOrCallTheNetwork() {
        noClasses().that().resideInAPackage("dev.proofstack.adapter.in.mcp..")
                .should().dependOnClassesThat().resideInAnyPackage("java.io..", "java.nio.file..", "java.net..",
                        "dev.proofstack.core.port.out..", "dev.proofstack.core.application..", "dev.proofstack.adapter.out..")
                .check(classes);
    }

    @Test
    void coreStaysFrameworkFree() {
        noClasses().that().resideInAPackage("dev.proofstack.core..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "dev.proofstack.adapter..")
                .check(classes);
    }
}
