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

    /** A date may be a year alone (spec proofstack-year-dates): the field takes it, and the file keeps it as a year. */
    @Test
    void aDateCanBeJustAYear() throws IOException {
        open();
        section("Experiencia");
        expand("Backend Engineer");

        page.getByLabel("Inicio *").fill("2021");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("startDate: \"2021\"");
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

    /** Spec proofstack-studio-merge R1.1–R1.2, R1.5: the change on disk is merged in, and nothing is saved by itself. */
    @Test
    void aChangeOnDiskIsMergedWithMyEditAndNeverOverwritten() throws IOException {
        expectedFailures.add(409);
        open();
        String theirs = file().replace("Jane Doe", "Grace Hopper");
        Files.writeString(profile, theirs);

        page.getByLabel("Titular *").fill("Staff Engineer");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("se han combinado"));
        assertThat(page.getByLabel("Nombre *")).hasValue("Grace Hopper");
        assertThat(page.getByLabel("Titular *")).hasValue("Staff Engineer");
        assertThat(file()).as("the merge alone writes nothing").isEqualTo(theirs);

        button("Guardar Ctrl+S").click();
        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("name: Grace Hopper").contains("headline: Staff Engineer");
    }

    /** R1.3: the same field changed on both sides is a question for the user. */
    @Test
    void aConflictIsResolvedByTheUser() throws IOException {
        expectedFailures.add(409);
        open();
        Files.writeString(profile, file().replace("headline: Backend Engineer", "headline: Engineering Lead"));

        page.getByLabel("Titular *").fill("Staff Engineer");
        button("Guardar Ctrl+S").click();

        Locator dialog = page.getByRole(AriaRole.DIALOG);
        assertThat(dialog).isVisible();
        assertThat(dialog.getByRole(AriaRole.GROUP)).hasCount(1);
        dialog.getByLabel(Pattern.compile("^Lo que hay en disco")).check();
        dialog.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Aplicar")).click();

        assertThat(dialog).isHidden();
        assertThat(page.getByLabel("Titular *")).hasValue(Pattern.compile("^Engineering Lead"));
        button("Guardar Ctrl+S").click();
        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("headline: Engineering Lead");
    }

    /**
     * Review H1: a new item's id follows its whole name, typed key by key, while the user does not touch the id,
     * and it stays unique in the list. It used to freeze on the first key ("p"), so two items clashed.
     */
    @Test
    void aNewItemGetsItsIdFromItsWholeNameAndIdsStayUnique() throws IOException {
        open();
        section("Proyectos");

        for (String name : new String[]{"Proofstack", "Pedidos", "Proofstack"}) {
            button("Añadir proyecto").click();
            Locator item = page.locator("#editor details[open]").last();
            item.getByLabel("Nombre *").pressSequentially(name);
            item.getByLabel("Resumen *").fill("Un resumen.");
        }
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).containsPattern("(?m)id: proofstack$").containsPattern("(?m)id: pedidos$")
                .containsPattern("(?m)id: proofstack-2$");
    }

    /** Review H1: an id the user writes is theirs; renaming the item afterwards does not change it. */
    @Test
    void anIdWrittenByTheUserIsKept() throws IOException {
        open();
        section("Proyectos");

        button("Añadir proyecto").click();
        Locator item = page.locator("#editor details[open]").last();
        item.getByLabel("Nombre *").pressSequentially("Proofstack");
        item.getByLabel("Identificador *").fill("portfolio-as-code");
        item.getByLabel("Nombre *").fill("Proofstack CLI"); // renaming afterwards must not touch the id
        item.getByLabel("Resumen *").fill("Un resumen.");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).containsPattern("(?m)id: portfolio-as-code$").contains("name: Proofstack CLI");
    }

    /** Review H3: adding an item to a list inside another item keeps the outer item open, so the new one is usable. */
    @Test
    void addingALinkInsideAProjectKeepsTheProjectOpen() throws IOException {
        open();
        section("Proyectos");
        expand("Event-driven billing");

        button("Añadir enlace").click();
        Locator link = page.locator("#editor details[open] details[open]").last();
        link.getByLabel("Texto del enlace *").fill("Repo");
        link.getByLabel("URL *").fill("https://github.com/example/billing");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("https://github.com/example/billing");
    }

    /** Review H4: "current job" and an end date exclude each other in the form, as they do in the schema. */
    @Test
    void theCurrentJobAndAnEndDateExcludeEachOther() throws IOException {
        open();
        section("Experiencia");
        expand("Backend Engineer");
        Locator current = page.getByLabel("Es mi puesto actual");
        Locator end = page.getByLabel("Fin", new Page.GetByLabelOptions().setExact(true));

        assertThat(end).isDisabled();
        current.uncheck();
        assertThat(end).isEnabled();
        end.fill("2024-06");
        button("Guardar Ctrl+S").click();

        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).containsPattern("endDate: \"?2024-06\"?").doesNotContain("current: true");

        current.check();
        assertThat(end).isDisabled();
        assertThat(end).hasValue("");
    }

    /** R2.1: my work can always leave the browser. */
    @Test
    void myChangesCanBeDownloaded() throws IOException {
        open();
        page.getByLabel("Titular *").fill("Staff Engineer");

        var download = page.waitForDownload(() -> button("Descargar mis cambios").click());

        assertThat(download.suggestedFilename()).isEqualTo("profile.edited.json");
        assertThat(Files.readString(download.path())).contains("\"headline\": \"Staff Engineer\"");
    }

    /** R2.2: an accidental reload of the tab does not lose what was not saved. */
    @Test
    void unsavedChangesSurviveReloadingTheTab() {
        open();
        page.getByLabel("Titular *").fill("Staff Engineer");

        java.util.List<String> dialogs = new java.util.concurrent.CopyOnWriteArrayList<>();
        page.onDialog(d -> {
            dialogs.add(d.type() + ": " + d.message());
            d.accept(); // "leave the page?" and then "recover the changes?"
        });
        page.reload();

        assertThat(page.getByLabel("Titular *")).hasValue("Staff Engineer");
        assertThat(button("Guardar Ctrl+S")).isEnabled();
        assertThat(dialogs).anyMatch(d -> d.startsWith("confirm: Hay cambios sin guardar"));
    }
}
