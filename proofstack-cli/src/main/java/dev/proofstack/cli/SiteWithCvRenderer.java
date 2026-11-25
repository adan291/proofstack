package dev.proofstack.cli;

import java.util.ArrayList;
import java.util.List;

import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.PortfolioRenderer;

/**
 * The portfolio site plus its CV in a sub-directory, so one {@code build} publishes both.
 * Lives in the composition root: the web and CV adapters stay independent of each other.
 */
final class SiteWithCvRenderer implements PortfolioRenderer {

    static final String CV_DIR = "cv/";

    private final PortfolioRenderer site;
    private final PortfolioRenderer cv;

    SiteWithCvRenderer(PortfolioRenderer site, PortfolioRenderer cv) {
        this.site = site;
        this.cv = cv;
    }

    @Override
    public List<Artifact> render(Profile profile) {
        List<Artifact> artifacts = new ArrayList<>(site.render(profile));
        cv.render(profile).forEach(a -> artifacts.add(a.under(CV_DIR)));
        return List.copyOf(artifacts);
    }
}
