package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;

import dev.proofstack.adapter.in.studio.StudioServer;

/**
 * Base of the browser tests: Studio started the way {@code proofstack studio} starts it, on a copy of the example
 * profile, opened in the installed Chrome. A test fails if the page logs a JavaScript error or a CSP violation.
 */
abstract class StudioE2E {

    static final Path EXAMPLE = Path.of(System.getProperty("proofstack.repoRoot", "..")).resolve("profiles/example/profile.yaml");

    private static Playwright playwright;
    private static Browser browser;

    @TempDir
    Path tmp;

    Path profile;
    StudioServer studio;
    BrowserContext context;
    Page page;
    final List<String> problems = new CopyOnWriteArrayList<>();
    /** Statuses a test provokes on purpose (422, 409...): not a problem, and the browser logs them as errors. */
    final java.util.Set<Integer> expectedFailures = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @BeforeAll
    static void launchChrome() {
        try {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setChannel("chrome"));
        } catch (PlaywrightException e) {
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
            Assumptions.abort("Chrome is not installed: the browser tests need it (" + firstLine(e) + ")");
        }
    }

    @AfterAll
    static void closeChrome() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void startStudio() throws IOException {
        profile = tmp.resolve("profile.yaml");
        Files.copy(EXAMPLE, profile);
        studio = StudioCommand.start(profile, 0);
        context = browser.newContext();
        page = context.newPage();
        page.onConsoleMessage(m -> {
            // Tolerated only in tests that provoke an error status on purpose; onResponse names the URL of the others.
            // Some requests (the favicon) never reach onResponse, so this message is the only trace they leave.
            boolean provoked = m.text().startsWith("Failed to load resource") && !expectedFailures.isEmpty();
            if (m.type().equals("error") && !provoked) {
                problems.add("console: " + m.text());
            }
        });
        page.onPageError(e -> problems.add("page error: " + e));
        context.onResponse(r -> {
            if (r.status() >= 400 && !expectedFailures.contains(r.status())) {
                problems.add("HTTP " + r.status() + " " + r.url().replaceAll("#.*", ""));
            }
        });
        // CSP violations are reported as console errors too, but this also catches them in the preview frame.
        page.addInitScript("document.addEventListener('securitypolicyviolation', e => "
                + "console.error('CSP violation: ' + e.violatedDirective + ' ' + e.blockedURI), true);");
    }

    @AfterEach
    void stopStudio() {
        if (context != null) {
            context.close();
        }
        if (studio != null) {
            studio.close();
        }
        assertThat(problems).as("JavaScript errors or CSP violations").isEmpty();
    }

    /** Opens the URL Studio prints, token included, and waits until the profile is loaded. */
    void open() {
        page.navigate(studio.url().toString());
        status().waitFor();
        com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat(status()).hasText("Perfil cargado.");
    }

    com.microsoft.playwright.Locator status() {
        return page.locator("#status");
    }

    String file() throws IOException {
        return Files.readString(profile);
    }

    private static String firstLine(Throwable e) {
        String message = String.valueOf(e.getMessage());
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
