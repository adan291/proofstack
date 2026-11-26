package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** R3: usable with the keyboard, and no serious or critical findings from axe-core. */
class StudioAccessibilityE2E extends StudioE2E {

    private static final String AXE = "/META-INF/resources/webjars/axe-core/4.14.0/axe.min.js";

    @Test
    void theKeyboardReachesTheEditorThroughTheSkipLink() {
        open();

        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Saltar al editor"))).isFocused();

        page.keyboard().press("Enter");
        page.keyboard().press("Tab");
        assertThat(page.getByLabel("Nombre *")).isFocused();
    }

    @Test
    void axeFindsNothingSeriousInAnySection() throws IOException {
        open();
        // Evaluated through the DevTools protocol, so the page's CSP (no inline scripts) still applies to the page.
        page.evaluate(axeSource());

        for (String section : List.of("Persona", "Experiencia", "Proyectos", "Certificaciones")) {
            page.getByRole(AriaRole.NAVIGATION).getByRole(AriaRole.BUTTON,
                    new com.microsoft.playwright.Locator.GetByRoleOptions()
                            .setName(java.util.regex.Pattern.compile("^" + section))).click();
            page.locator("#editor details").all().forEach(d -> d.evaluate("d => d.open = true"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> violations = (List<Map<String, Object>>) page.evaluate(
                    "async () => (await axe.run(document, { resultTypes: ['violations'] })).violations"
                            + ".filter(v => v.impact === 'serious' || v.impact === 'critical')"
                            + ".map(v => ({ id: v.id, help: v.help, nodes: v.nodes.map(n => n.target.join(' ')).slice(0, 3) }))");

            assertThat(violations).as(section).isEmpty();
        }
    }

    @Test
    void theConflictDialogIsAccessibleToo() throws IOException {
        expectedFailures.add(409);
        open();
        java.nio.file.Files.writeString(profile, file().replace("headline: Backend Engineer", "headline: Engineering Lead"));
        page.getByLabel("Titular *").fill("Staff Engineer");
        page.keyboard().press("Control+S");
        assertThat(page.getByRole(AriaRole.DIALOG)).isVisible();
        page.evaluate(axeSource());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> violations = (List<Map<String, Object>>) page.evaluate(
                "async () => (await axe.run('#merge', { resultTypes: ['violations'] })).violations"
                        + ".filter(v => v.impact === 'serious' || v.impact === 'critical').map(v => v.id)");

        assertThat(violations).isEmpty();
        assertThat(page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(
                java.util.regex.Pattern.compile("^Lo tuyo")))).isFocused();
    }

    private static String axeSource() throws IOException {
        try (InputStream in = StudioAccessibilityE2E.class.getResourceAsStream(AXE)) {
            if (in == null) {
                throw new IOException("axe-core webjar not on the test classpath: " + AXE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
