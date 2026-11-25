package dev.proofstack.cli;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import org.junit.jupiter.api.Test;

import com.microsoft.playwright.options.AriaRole;

/** R1.1: the URL Studio prints opens the editor with the profile loaded. */
class StudioLoadsE2E extends StudioE2E {

    @Test
    void theEditorLoadsTheProfile() {
        open();

        assertThat(page.getByRole(AriaRole.NAVIGATION, new com.microsoft.playwright.Page.GetByRoleOptions()
                .setName("Secciones del perfil"))).isVisible();
        assertThat(page.getByLabel("Nombre *")).hasValue("Jane Doe");
    }
}
