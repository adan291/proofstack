package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.FrameLocator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

/** R2: doctor findings next to their field, and the preview of the saved CV and site. */
class StudioPreviewE2E extends StudioE2E {

    @Test
    void findingsAreListedAndLeadToTheirField() {
        open();

        assertThat(page.locator("#issues-count")).hasText(Pattern.compile("2 errores"));
        // The example's sample email is flagged in the list and next to the field.
        assertThat(page.locator("#editor .field-msg--error")).containsText("sample email address");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions()
                .setName(Pattern.compile("^\\$\\.certifications\\[0\\]\\.link\\.url"))).click();

        // Inside a closed list item: the item opens, and a URL is a one-line url input.
        assertThat(page.locator("input[type=url][data-path='$.certifications[0].link.url']")).isFocused();
    }

    @Test
    void bothPreviewsLoadStyledAndFollowTheSavedProfile() {
        open();
        FrameLocator preview = page.frameLocator("#preview-frame");

        assertThat(preview.locator("h1")).hasText("Jane Doe");
        assertThat(preview.locator("h1")).hasCSS("font-weight", Pattern.compile("[6-9]00|bold"));

        page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Web")).click();
        assertThat(preview.locator("h1")).containsText("Jane Doe");
        assertThat(preview.locator("link[rel=stylesheet]")).hasCount(1);

        page.getByLabel("Nombre *").fill("Grace Hopper");
        page.keyboard().press("Control+S");
        assertThat(status()).hasText(Pattern.compile("^Guardado"));

        assertThat(preview.locator("h1")).containsText("Grace Hopper");
    }
}
