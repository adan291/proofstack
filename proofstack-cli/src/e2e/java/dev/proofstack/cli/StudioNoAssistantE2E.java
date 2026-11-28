package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Without a provider Studio is what it was: no AI button in any field. */
class StudioNoAssistantE2E extends AiE2E {

    @Override
    Map<String, String> providerEnvironment(String baseUrl) {
        return Map.of();
    }

    @Test
    void thereIsNoAiButtonInAnySection() {
        open();
        for (String section : new String[] {"Persona", "Experiencia", "Proyectos", "Evidencias", "Labs"}) {
            section(section);
            page.locator("#editor details").all().forEach(d -> d.evaluate("d => d.open = true"));
            assertThat(aiButtons()).hasCount(0);
            assertThat(page.locator(".ai__btn")).hasCount(0);
        }
    }

    @Test
    void theStatusSaysNotConfiguredAndTheOnlyEntryPointIsTheSettingsButton() {
        open();
        assertThat(button("Ajustes de IA")).isVisible();
        Object configured = page.evaluate("async () => (await (await fetch('/api/ai/status', "
                + "{ headers: { 'X-Studio-Token': sessionStorage.getItem('studio-token') } })).json()).configured");
        org.assertj.core.api.Assertions.assertThat(configured).isEqualTo(false);
    }
}
