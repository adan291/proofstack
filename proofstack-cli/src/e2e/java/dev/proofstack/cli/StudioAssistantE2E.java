package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** The AI assistant in Chrome, with a provider configured through the environment (a fake one on this machine). */
class StudioAssistantE2E extends AiE2E {

    private static final String KEY = "sk-e2e-never-in-the-browser-123456";
    private static final String PROPOSAL = "Texto propuesto por la IA.";

    @Override
    Map<String, String> providerEnvironment(String baseUrl) {
        return Map.of("PROOFSTACK_AI_PROTOCOL", "openai", "PROOFSTACK_AI_BASE_URL", baseUrl,
                "PROOFSTACK_AI_MODEL", "fake-model", "PROOFSTACK_AI_API_KEY", KEY);
    }

    @Override
    boolean withOtherLanguageFile() {
        return true;
    }

    @Test
    void improveAcceptAndSaveWritesTheFile() throws IOException {
        open();
        String before = file();
        openSummaryPanel();
        exactButton("Mejorar").click();

        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        assertThat(page.locator("#ai-changes")).containsText("Acorté la frase.");
        assertThat(page.locator("#ai-questions")).containsText("¿Cuántos usuarios tiene?");
        assertThat(page.locator("#ai-warning")).isHidden();
        assertThat(page.locator("#ai-result-title")).isFocused();
        // Nothing changes until Aceptar, and the file not until Guardar.
        assertThat(summaryField()).not().hasValue(PROPOSAL);
        assertThat(file()).isEqualTo(before);

        exactButton("Aceptar").click();
        assertThat(panel()).isHidden();
        assertThat(summaryField()).hasValue(PROPOSAL);
        assertThat(summaryField()).isFocused();
        assertThat(file()).isEqualTo(before);
        assertThat(button("Guardar Ctrl+S")).isEnabled();

        button("Guardar Ctrl+S").click();
        assertThat(status()).hasText(Pattern.compile("^Guardado"));
        assertThat(file()).contains("summary: " + PROPOSAL);
    }

    @Test
    void theContextSentToTheModelHasNoEmailAndTheKeyNeverReachesTheBrowser() throws IOException {
        List<String> apiBodies = new CopyOnWriteArrayList<>();
        context.onResponse(r -> {
            if (r.url().contains("/api/")) {
                try {
                    apiBodies.add(r.text());
                } catch (RuntimeException e) {
                    // a body that is gone (navigation) cannot leak anything either
                }
            }
        });
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);

        String sent = provider.requests.getFirst();
        assertThat(sent).contains("Jane Doe").doesNotContain("jane.doe@example.com");
        assertThat(provider.authorization).containsExactly("Bearer " + KEY);
        assertThat(apiBodies).isNotEmpty().noneMatch(body -> body.contains(KEY));
        assertThat(file()).doesNotContain(KEY);
    }

    @Test
    void discardingChangesNothing() throws IOException {
        open();
        String before = file();
        String field = summaryField().inputValue();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);

        exactButton("Descartar").click();
        assertThat(panel()).isHidden();
        assertThat(summaryField()).hasValue(field);
        assertThat(button("Guardar Ctrl+S")).isDisabled();
        assertThat(file()).isEqualTo(before);
        assertThat(button("Mejorar con IA: Resumen").first()).isFocused();
    }

    @Test
    void theProposalCanBeEditedBeforeAccepting() throws IOException {
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        button("Editar antes de aceptar").click();
        page.locator("#ai-edit").fill("Mi versión final.");
        exactButton("Aceptar").click();
        assertThat(summaryField()).hasValue("Mi versión final.");
    }

    @Test
    void anInventedNumberShowsAWarning() {
        provider.answer = prompt -> "{\"text\":\"Redujo la latencia un 40% y sirvió a 1200 usuarios.\",\"changes\":[],\"questions\":[]}";
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-warning")).isVisible();
        assertThat(page.locator("#ai-warning")).containsText("40%");
        assertThat(page.locator("#ai-warning")).containsText("1200");
    }

    @Test
    void aShortcutAndTheRequestTravelToTheModel() {
        open();
        openSummaryPanel();
        page.locator("#ai-request").fill("hazlo para un banco");
        exactButton("Más corto").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        String prompt = provider.requests.getFirst();
        assertThat(prompt).contains("Make it shorter").contains("hazlo para un banco");
    }

    @Test
    void theRequestCanBeSentWithTheSendButton() {
        open();
        openSummaryPanel();
        page.locator("#ai-request").fill("hazlo mas claro");
        exactButton("Enviar").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        assertThat(provider.requests.getFirst()).contains("hazlo mas claro");
    }

    @Test
    void aJobOfferAsksForThePastedOffer() {
        open();
        openSummaryPanel();
        exactButton("Para una oferta").click();
        page.locator("#ai-offer").fill("Buscamos experiencia con Kafka y PostgreSQL.");
        button("Adaptar a la oferta").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        assertThat(provider.requests.getFirst()).contains("Buscamos experiencia con Kafka").contains("highlight what matches");
    }

    @Test
    void translatingWritesIntoTheSameFieldOfTheOtherFile() throws IOException {
        provider.answer = prompt -> "{\"text\":\"Translated summary.\",\"changes\":[],\"questions\":[]}";
        open();
        String before = file();
        openSummaryPanel();
        exactButton("Traducir al inglés").click();
        assertThat(page.locator("#ai-proposal")).hasText("Translated summary.");
        assertThat(page.locator("#ai-target-note")).containsText("profile.en.yaml");
        assertThat(provider.requests.getFirst()).contains("Translate");

        exactButton("Guardar en profile.en.yaml").click();
        assertThat(panel()).isHidden();
        assertThat(status()).hasText("Traducción guardada en profile.en.yaml.");
        assertThat(Files.readString(tmp.resolve("profile.en.yaml"))).contains("Translated summary.");
        assertThat(file()).isEqualTo(before);
        assertThat(button("Guardar Ctrl+S")).isDisabled();
    }

    @Test
    void withoutTheOtherFileThereIsNoTranslateButton() throws IOException {
        Files.delete(tmp.resolve("profile.en.yaml"));
        open();
        openSummaryPanel();
        assertThat(exactButton("Mejorar")).isVisible();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("^Traducir")))).hasCount(0);
    }

    @Test
    void aRequestCanBeCancelled() {
        provider.delayMillis = 3000;
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-state")).containsText("Pensando");
        button("Cancelar la petición").click();
        assertThat(page.locator("#ai-state")).containsText("Petición cancelada");
        assertThat(page.locator("#ai-request")).isFocused();
        assertThat(page.locator("#ai-result")).isHidden();
    }

    @Test
    void aFailureIsExplainedInTheUsersLanguage() {
        expectedFailures.add(502);
        provider.answer = prompt -> "this is not json";
        open();
        openSummaryPanel();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-state")).containsText("formato utilizable");
        assertThat(page.locator("#ai-state")).isFocused();
        assertThat(page.locator("#ai-result")).isHidden();
    }

    @Test
    void textListsLetYouPickTheLine() throws IOException {
        provider.answer = prompt -> "{\"text\":\"Aprendizaje afinado.\",\"changes\":[],\"questions\":[]}";
        open();
        section("Proyectos");
        page.locator("#editor details > summary").first().click();
        button("Mejorar con IA: Aprendizajes").first().click();
        assertThat(page.locator("#ai-line")).isVisible();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-proposal")).hasText("Aprendizaje afinado.");
        assertThat(provider.requests.getFirst()).contains("projects[billing-events].learnings[0]");
        exactButton("Aceptar").click();
        assertThat(page.locator("#editor textarea[data-path='$.projects[0].learnings']")).hasValue("Aprendizaje afinado.");
    }

    @Test
    void axeFindsNothingSeriousWithTheButtonsThePanelOrTheSettings() throws IOException {
        open();
        assertThat(seriousFindings("document")).as("persona con botones").isEmpty();
        openSummaryPanel();
        assertThat(seriousFindings("#ai-panel")).as("panel sin propuesta").isEmpty();
        exactButton("Mejorar").click();
        assertThat(page.locator("#ai-proposal")).hasText(PROPOSAL);
        assertThat(seriousFindings("#ai-panel")).as("panel con propuesta").isEmpty();
        exactButton("Descartar").click();
        button("Ajustes de IA").click();
        assertThat(page.locator("#ai-settings")).isVisible();
        assertThat(seriousFindings("#ai-settings")).as("ajustes").isEmpty();
    }
}
