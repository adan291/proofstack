package dev.proofstack.core.domain;

/** A certification or licence. Only the title is required; the link lets a reader verify the credential. */
public record Certification(String title, String issuer, ProfileDate issued, Link link) {

    public Certification {
        title = Checks.requireText(title, "title");
        issuer = Checks.optionalText(issuer);
    }
}
