package dev.proofstack.adapter.in.studio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** The editor is plain files: no build, no third-party code, nothing loaded from outside 127.0.0.1. */
class StudioUiTest {

    private static String resource(String name) throws IOException {
        try (InputStream in = StudioUiTest.class.getResourceAsStream("/studio/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void loadsNothingFromOutside() throws IOException {
        for (String name : new String[]{"index.html", "studio.js", "schema-form.js", "studio.css"}) {
            String text = resource(name);
            assertThat(text).as(name).doesNotContainPattern("(src|href)=\"(https?:)?//")
                    .doesNotContainPattern("import [^;]*from '(https?:)?//")
                    .doesNotContain("@import").doesNotContain("eval(").doesNotContain("innerHTML");
        }
    }

    @Test
    void everyAssetThePageAsksForIsServed() throws IOException {
        Matcher m = Pattern.compile("(?:src|href)=\"/assets/([^\"]+)\"").matcher(resource("index.html"));
        int found = 0;
        while (m.find()) {
            assertThat(StudioServer.class.getResource("/studio/" + m.group(1))).as(m.group(1)).isNotNull();
            found++;
        }
        assertThat(found).isEqualTo(2);
        assertThat(resource("studio.js")).contains("from './schema-form.js'");
    }

    @Test
    void htmlHasNoInlineScriptsOrStylesSoTheStrictCspHolds() throws IOException {
        String html = resource("index.html");

        assertThat(html).doesNotContainPattern("<script(?![^>]*src=)").doesNotContain("<style").doesNotContain(" style=\"")
                .doesNotContainPattern(" on[a-z]+=\"");
        assertThat(html).contains("<html lang=\"es\">").contains("role=\"status\"").contains("aria-live=\"polite\"")
                .contains("sandbox=\"allow-scripts\"");
    }
}
