package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** R1: editing, saving and the errors a user can meet, in Chrome. */
class StudioEditingE2E extends StudioE2E {

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    /** List items are closed {@code <details>}: like a user, open one before using its buttons. */
    private void expand(String title) {
        page.locator("#editor details > summary", new Page.LocatorOptions().setHasText(title)).first().click();
    }

    private void section(String name) {
        page.getByRole(AriaRole.NAVIGATION).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(Pattern.compile("^" + name))).click();
    }

    @Test
    void aFieldEditedAndSavedReachesTheFile() throws IOException {
        open();

        page.getByLabel("Titular *").fill("Staff Engineer");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("headline: Staff Engineer");
        assertThat(button("Guardar Ctrl+S")).isDisabled();
    }

    @Test
    void ctrlSSaves() throws IOException {
        open();

        page.getByLabel("Ubicación").fill("Madrid");
        page.keyboard().press("Control+S");

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("location: Madrid");
    }

    @Test
    void listItemsCanBeAddedRemovedAndReordered() throws IOException {
        open();
        section("Habilidades");

        expand("Java 25");
        button("Eliminar Java 25").click();
        expand("Spring Boot");
        button("Bajar Spring Boot").click();
        button("Añadir habilidad").click();
        page.locator("#editor details[open]").last().getByLabel("Nombre *").fill("Go");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        String yaml = file();
        assertThat(yaml).doesNotContain("Java 25").contains("name: Go");
        assertThat(yaml.indexOf("Hexagonal architecture")).isLessThan(yaml.indexOf("Spring Boot"));
    }

    @Test
    void anInvalidDocumentIsNotSavedAndTheFieldIsMarked() throws IOException {
        expectedFailures.add(422);
        String before = file();
        open();

        page.getByLabel("Nombre *").fill("");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^No se guardó"));
        assertThat(page.locator("#editor fieldset[data-path='$.person']").first()).isVisible();
        assertThat(page.locator("#editor [aria-invalid='true']").first()).isVisible();
        assertThat(file()).isEqualTo(before);
    }

    @Test
    void withoutTheTokenItSaysWhatToOpen() {
        expectedFailures.add(401);
        page.navigate(studio.url().toString().replaceAll("#.*", ""));

        assertThat(status()).hasText(Pattern.compile("Falta el token"));
    }

    @Test
    void aFileThatIsNotYamlSaysWhereItBreaks() throws IOException {
        expectedFailures.add(422);
        Files.writeString(profile, "language: en\nperson:\n  name: Jane: Doe\n");

        page.navigate(studio.url().toString());

        assertThat(status()).hasText(Pattern.compile("no es YAML válido.*line 3"));
    }

    @Test
    void aChangeOnDiskIsNeverOverwritten() throws IOException {
        expectedFailures.add(409);
        open();
        String theirs = file().replace("Jane Doe", "Grace Hopper");
        Files.writeString(profile, theirs);

        page.getByLabel("Titular *").fill("Staff Engineer");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("cambió en disco"));
        assertThat(button("Recargar desde el disco")).isVisible();
        assertThat(file()).isEqualTo(theirs);
    }
}
