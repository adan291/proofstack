package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Configuring the assistant from the settings screen, starting from nothing. */
class StudioAiSettingsE2E extends AiE2E {

    @Override
    Map<String, String> providerEnvironment(String baseUrl) {
        return Map.of();
    }

    @Test
    void aCustomProviderIsConfiguredTestedAndTurnsTheButtonsOn() throws IOException {
        open();
        assertThat(aiButtons()).hasCount(0);

        button("Ajustes de IA").click();
        assertThat(page.locator("#ai-settings")).isVisible();
        page.locator("#ai-preset").selectOption("custom");
        page.locator("#ai-url").fill(provider.baseUrl("127.0.0.1"));
        page.locator("#ai-model").fill("fake-model");
        page.locator("input[name='ai-key'][value='none']").check();
        page.locator("#ai-settings-test").click();

        assertThat(page.locator("#ai-settings-msg")).containsText("Conexión correcta con el modelo fake-model");
        String saved = Files.readString(home.resolve("ai.json"));
        assertThat(saved).contains("\"protocol\"").contains("fake-model").doesNotContain("apiKey\" : \"");

        page.locator("#ai-settings-close").click();
        assertThat(aiButtons().first()).isVisible();
        assertThat(button("Ajustes de IA")).isFocused();
    }

    @Test
    void aKeyStoredInTheFileIsWarnedAboutAndNeverComesBack() throws IOException {
        open();
        button("Ajustes de IA").click();
        page.locator("#ai-preset").selectOption("custom");
        page.locator("#ai-url").fill(provider.baseUrl("127.0.0.1"));
        page.locator("#ai-model").fill("fake-model");
        page.locator("input[name='ai-key'][value='file']").check();
        page.locator("#ai-key-value").fill("sk-typed-in-the-form-123456");
        page.locator("#ai-settings-save").click();
        assertThat(page.locator("#ai-settings-msg")).containsText("texto plano");
        assertThat(page.locator("#ai-key-value")).hasValue("");

        page.locator("#ai-settings-close").click();
        button("Ajustes de IA").click();
        assertThat(page.locator("#ai-key-value")).hasValue("");
        assertThat(page.locator("#ai-key-value")).hasAttribute("placeholder", java.util.regex.Pattern.compile("Hay una clave guardada"));
        assertThat(Files.readString(home.resolve("ai.json"))).contains("sk-typed-in-the-form-123456");
        assertThat(file()).doesNotContain("sk-typed");
    }

    @Test
    void anInvalidUrlIsRefusedWithAReason() throws IOException {
        expectedFailures.add(400);
        open();
        button("Ajustes de IA").click();
        page.locator("#ai-url").fill("ftp://example.com");
        page.locator("#ai-settings-save").click();
        assertThat(page.locator("#ai-settings-msg")).containsText("http");
        org.assertj.core.api.Assertions.assertThat(Files.exists(home.resolve("ai.json"))).isFalse();
    }
}
