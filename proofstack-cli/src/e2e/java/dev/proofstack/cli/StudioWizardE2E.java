package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.sun.net.httpserver.HttpServer;

/** The setup assistant in Chrome, end to end: GitHub against a local fake, the LinkedIn app, a project, publishing. */
class StudioWizardE2E extends StudioE2E {

    private static final String AXE = "/META-INF/resources/webjars/axe-core/4.14.0/axe.min.js";
    private HttpServer github;
    private Path home;

    @BeforeEach
    void studioWithFakeGitHub() throws IOException {
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/users/", exchange -> {
            byte[] body = ("[{\"name\":\"coffee-bot\",\"description\":\"Coffee through MQTT\",\"language\":\"Java\","
                    + "\"html_url\":\"https://github.com/example/coffee-bot\"},"
                    + "{\"name\":\"old-homework\",\"language\":\"C\",\"html_url\":\"https://github.com/example/old-homework\"},"
                    + "{\"name\":\"dotfiles\",\"html_url\":\"https://github.com/example/dotfiles\"}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        github.start();
        home = tmp.resolve("home");
        studio.close();
        studio = StudioCommand.start(profile, 0, Map.of(
                "PROOFSTACK_GITHUB_API", "http://127.0.0.1:" + github.getAddress().getPort(),
                "PROOFSTACK_HOME", home.toString()), new PrintWriter(Writer.nullWriter()));
    }

    @AfterEach
    void stopGitHub() {
        github.stop(0);
    }

    private Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    private void openWizard() {
        page.navigate(studio.wizardUrl().toString());
        assertThat(page.locator("#app")).isVisible();
    }

    @Test
    void fromRepositoriesToAFinishedProjectEveryStepReachesTheFile() throws IOException {
        openWizard();
        // The example profile is complete: the assistant resumes at the last step.
        assertThat(page.locator("#publish-title")).isVisible();

        button("2. GitHub").click();
        assertThat(page.getByLabel("Tu usuario de GitHub")).hasValue("example");
        button("Buscar mis repositorios").click();
        assertThat(page.locator("#github-list li")).hasCount(3);
        page.getByLabel("Filtrar").fill("coffee");
        assertThat(page.locator("#github-list li:not([hidden])")).hasCount(1);
        page.locator("#github-list input[value='coffee-bot']").check();
        assertThat(page.locator("#github-count")).hasText("1 de 3 marcados.");
        button("Añadir los marcados y seguir").click();

        assertThat(page.locator("#linkedin-title")).isVisible();
        assertThat(file()).contains("id: coffee-bot").doesNotContain("old-homework");
        assertThat(Files.readString(profile.resolveSibling(GitHubIgnore.FILE))).contains("old-homework", "dotfiles");

        page.getByLabel("Client ID").fill("my-app");
        page.getByLabel("Client Secret").fill("s3cret");
        button("Guardar la app").click();
        assertThat(button("Traer de LinkedIn")).isEnabled();
        assertThat(Files.readString(home.resolve("linkedin.properties"))).contains("my-app");
        assertThat(page.getByLabel("Client Secret")).hasValue("");

        button("4. Proyectos").click();
        assertThat(page.locator("#project-counter")).hasText("Proyecto 1 de 1 por completar");
        assertThat(page.locator("#project-name")).hasText("coffee-bot");
        assertThat(page.getByLabel("Qué es (una frase) *")).hasValue("Coffee through MQTT");
        page.getByLabel("Qué problema resolvía").fill("Making coffee needed someone in the kitchen.");
        page.getByLabel("Cómo lo resolviste").fill("An ESP32 listens on MQTT and switches the machine on.");
        page.getByLabel("Qué aprendiste").fill("Retained MQTT messages need a clear owner.");
        button("Guardar y siguiente").click();

        assertThat(page.locator("#publish-title")).isVisible();
        assertThat(status()).hasText(Pattern.compile("Proyecto completo"));
        String yaml = file();
        assertThat(yaml).contains("Making coffee needed someone in the kitchen.").doesNotContain("TODO");
        assertThat(new dev.proofstack.adapter.yaml.YamlProfileSource().load(profile).projects())
                .anySatisfy(p -> assertThat(p.id()).isEqualTo("coffee-bot"));
    }

    @Test
    void anEmptyFieldKeepsItsTodoAndTheProjectCanBeRemoved() throws IOException {
        openWizard();
        button("2. GitHub").click();
        button("Buscar mis repositorios").click();
        button("Marcar todos").click();
        button("Añadir los marcados y seguir").click();
        assertThat(page.locator("#linkedin-title")).isVisible();
        button("4. Proyectos").click();
        assertThat(page.locator("#project-counter")).hasText("Proyecto 1 de 3 por completar");
        page.onDialog(d -> d.accept());
        button("Quitar este proyecto").click();
        assertThat(page.locator("#project-counter")).hasText(Pattern.compile("de 2 por completar"));
        assertThat(Files.readString(profile.resolveSibling(GitHubIgnore.FILE))).contains("coffee-bot");
        assertThat(file()).doesNotContain("id: coffee-bot").contains("TODO");
    }

    @Test
    void axeFindsNothingSeriousInAnyStep() throws IOException {
        openWizard();
        String axe;
        try (InputStream in = StudioWizardE2E.class.getResourceAsStream(AXE)) {
            axe = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        page.evaluate(axe);
        for (String step : List.of("1. Tú", "2. GitHub", "3. LinkedIn", "4. Proyectos", "5. Publicar")) {
            button(step).click();
            @SuppressWarnings("unchecked")
            List<Object> violations = (List<Object>) page.evaluate(
                    "async () => (await axe.run(document, { resultTypes: ['violations'] })).violations"
                            + ".filter(v => v.impact === 'serious' || v.impact === 'critical')"
                            + ".map(v => v.id + ': ' + v.nodes.map(n => n.target.join(' ')).slice(0, 3))");
            assertThat(violations).as(step).isEmpty();
        }
    }
}
