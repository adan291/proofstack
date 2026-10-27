package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Profile;

import java.util.List;

/** Driven port: turns a profile into output artifacts (web, README, LinkedIn post...). */
public interface PortfolioRenderer {

    List<Artifact> render(Profile profile);
}
