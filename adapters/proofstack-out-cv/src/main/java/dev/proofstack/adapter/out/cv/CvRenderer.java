package dev.proofstack.adapter.out.cv;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.PortfolioRenderer;

/**
 * Driven adapter: renders a one-or-two page CV as {@code cv.md}, {@code cv.html} and {@code cv.css}.
 * The HTML is single column, text only, with a print stylesheet, so "Save as PDF" gives an ATS-friendly document.
 */
public final class CvRenderer implements PortfolioRenderer {

    private static final String BASE = "/cv/";

    private final CvOptions options;
    private final Template html;
    private final String css;

    public CvRenderer() {
        this(CvOptions.defaults());
    }

    public CvRenderer(CvOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        // escapeHTML(true) is the default; set explicitly because it is a security property of this adapter.
        Mustache.Compiler compiler = Mustache.compiler().escapeHTML(true).nullValue("").emptyStringIsFalse(true);
        try (Reader reader = new InputStreamReader(resource("cv.mustache"), StandardCharsets.UTF_8)) {
            this.html = compiler.compile(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.css = text("cv.css");
    }

    @Override
    public List<Artifact> render(Profile profile) {
        Cv cv = CvBuilder.build(profile, options, CvText.forLanguage(profile.language()));
        return List.of(
                new Artifact("cv.md", CvMarkdown.render(cv)),
                new Artifact("cv.html", html.execute(cv)),
                new Artifact("cv.css", css));
    }

    private static InputStream resource(String name) {
        InputStream in = CvRenderer.class.getResourceAsStream(BASE + name);
        if (in == null) {
            throw new IllegalStateException("Missing CV resource " + BASE + name);
        }
        return in;
    }

    private static String text(String name) {
        try (InputStream in = resource(name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
