package dev.proofstack.core.domain;

import java.time.YearMonth;

/** A certification or licence. Only the title is required; the link lets a reader verify the credential. */
public record Certification(String title, String issuer, YearMonth issued, Link link) {

    public Certification {
        title = Checks.requireText(title, "title");
        issuer = Checks.optionalText(issuer);
    }
}
