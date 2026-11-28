package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * A provider that is not on this machine ({@code 127.0.0.2} is not one of 127.0.0.1, localhost or ::1) needs the
 * author's consent before the first request, once per provider.
 */
class StudioAiConsentE2E extends AiE2E {

    @Override
    String providerHost() {
        return "127.0.0.2";
    }

    @Override
    Map<String, String> providerEnvironment(String baseUrl) {
        try {
            Files.writeString(tmp.resolve("home").resolve("ai.json"), "{\"allowInsecureHttp\": true}");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return Map.of("PROOFSTACK_AI_PROTOCOL", "openai", "PROOFSTACK_AI_BASE_URL", baseUrl,
                "PROOFSTACK_AI_MODEL", "fake-model");
    }

    @Override
    Map<String, String> environment() throws IOException {
        try {
            return super.environment();
        } catch (java.net.BindException e) {
            Assumptions.abort("127.0.0.2 is not available on this machine: " + e.getMessage());
            return Map.of();
        }
    }

    @Test
    void nothingIsSentUntilTheAuthorAccepts() throws IOException {
        expectedFailures.add(409); // consent-required is an answer, not a failure, but the browser logs it
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        provider.answer = prompt -> "{\"text\":\"Propuesta " + calls.incrementAndGet() + ".\",\"changes\":[],\"questions\":[]}";
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();

        assertThat(page.locator("#ai-consent")).isVisible();
        assertThat(page.locator("#ai-consent-text")).containsText("Tu perfil se enviará a 127.0.0.2 para generar la propuesta");
        assertThat(provider.requests).isEmpty();

        exactButton("No enviar nada").click();
        assertThat(provider.requests).isEmpty();

        exactButton("Mejorar").click();
        exactButton("Aceptar y continuar").click();
        assertThat(page.locator("#ai-proposal")).hasText("Propuesta 1.");
        assertThat(provider.requests).hasSize(1);
        assertThat(Files.readString(home().resolve("ai.json"))).contains("127.0.0.2");

        // Once per provider: the second request goes straight through.
        exactButton("Probar otra vez").click();
        assertThat(page.locator("#ai-proposal")).hasText("Propuesta 2.");
        assertThat(page.locator("#ai-consent")).isHidden();
        assertThat(provider.requests).hasSize(2);
    }

    private java.nio.file.Path home() {
        return tmp.resolve("home");
    }
}
