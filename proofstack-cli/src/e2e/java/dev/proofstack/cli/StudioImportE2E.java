package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.sun.net.httpserver.HttpServer;

/** The import buttons in Chrome: GitHub against a local fake, LinkedIn without an app configured. */
class StudioImportE2E extends StudioE2E {

    private HttpServer github;

    @BeforeEach
    void studioWithFakeGitHub() throws IOException {
        github = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        github.createContext("/users/", exchange -> {
            byte[] body = ("[{\"name\":\"coffee-bot\",\"description\":\"Coffee through MQTT\",\"language\":\"Java\","
                    + "\"html_url\":\"https://github.com/someone/coffee-bot\"}]").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        github.start();
        studio.close();
        studio = StudioCommand.start(profile, 0, Map.of(
                "PROOFSTACK_GITHUB_API", "http://127.0.0.1:" + github.getAddress().getPort(),
                "PROOFSTACK_HOME", tmp.resolve("home").toString()), new PrintWriter(Writer.nullWriter()));
    }

    @AfterEach
    void stopGitHub() {
        github.stop(0);
    }

    private com.microsoft.playwright.Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    @Test
    void gitHubRepositoriesArriveInTheDraftAndOnlyReachTheFileOnSave() throws IOException {
        open();
        String before = file();
        page.onDialog(d -> d.accept("someone")); // the example profile may not link a GitHub user: Studio asks
        button("Traer repos de GitHub").click();
        assertThat(page.locator("#imported-title")).hasText("GitHub: 1 novedad");
        assertThat(page.locator("#imported-added")).containsText("coffee-bot");
        assertThat(file()).isEqualTo(before);
        button("Entendido").click();
        assertThat(status()).hasText(Pattern.compile("^Importado"));
        button("Guardar Ctrl+S").click();
        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("id: coffee-bot").contains("https://github.com/someone/coffee-bot");
    }

    @Test
    void linkedInWithoutAnAppSaysHowToSetItUp() {
        expectedFailures.add(412);
        open();
        button("Traer de LinkedIn").click();
        assertThat(status()).hasText(Pattern.compile("proofstack setup"));
    }
}
