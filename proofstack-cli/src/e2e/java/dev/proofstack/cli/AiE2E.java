package dev.proofstack.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/**
 * Base of the AI assistant browser tests: a clean {@code PROOFSTACK_HOME}, and a fake provider that speaks the OpenAI
 * protocol on this machine. Subclasses decide which environment Studio gets.
 */
abstract class AiE2E extends StudioE2E {

    static final String AXE = "/META-INF/resources/webjars/axe-core/4.14.0/axe.min.js";

    FakeAiProvider provider;
    Path home;

    /** Host the fake provider listens on and Studio is told about. */
    String providerHost() {
        return "127.0.0.1";
    }

    /** Extra variables (the provider itself, a key...) on top of the clean home. */
    abstract Map<String, String> providerEnvironment(String baseUrl);

    /** Whether a {@code profile.en.yaml} exists next to the profile when Studio starts. */
    boolean withOtherLanguageFile() {
        return false;
    }

    @Override
    Map<String, String> environment() throws IOException {
        home = tmp.resolve("home");
        Files.createDirectories(home);
        provider = new FakeAiProvider(providerHost());
        if (withOtherLanguageFile()) {
            Files.copy(EXAMPLE, tmp.resolve("profile.en.yaml"));
        }
        java.util.Map<String, String> env = new java.util.HashMap<>(providerEnvironment(provider.baseUrl(providerHost())));
        env.put("PROOFSTACK_HOME", home.toString());
        return env;
    }

    @AfterEach
    void stopProvider() {
        if (provider != null) {
            provider.close();
        }
    }

    Locator button(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name));
    }

    Locator exactButton(String name) {
        return page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(name).setExact(true));
    }

    Locator aiButtons() {
        return page.locator("button[aria-label^='Mejorar con IA']");
    }

    Locator panel() {
        return page.locator("#ai-panel");
    }

    void section(String name) {
        page.getByRole(AriaRole.NAVIGATION).getByRole(AriaRole.BUTTON,
                new Locator.GetByRoleOptions().setName(Pattern.compile("^" + name))).click();
    }

    Locator summaryField() {
        return page.locator("#editor textarea[data-path='$.person.summary']");
    }

    /** Opens the panel of the person's summary. */
    void openSummaryPanel() {
        button("Mejorar con IA: Resumen").first().click();
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(panel()).isVisible();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> seriousFindings(String context) throws IOException {
        page.evaluate(axeSource());
        return (List<Map<String, Object>>) page.evaluate(
                "async (c) => (await axe.run(c === 'document' ? document : c, { resultTypes: ['violations'] })).violations"
                        + ".filter(v => v.impact === 'serious' || v.impact === 'critical')"
                        + ".map(v => ({ id: v.id, help: v.help, nodes: v.nodes.map(n => n.target.join(' ')).slice(0, 3) }))",
                context);
    }

    private static String axeSource() throws IOException {
        try (InputStream in = AiE2E.class.getResourceAsStream(AXE)) {
            if (in == null) {
                throw new IOException("axe-core webjar not on the test classpath: " + AXE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
