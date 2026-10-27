package dev.proofstack.core.domain;

import java.util.List;

/** Who the portfolio belongs to. {@code email} and {@code location} are optional. */
public record PersonalInfo(
        String name,
        String headline,
        String summary,
        String location,
        String email,
        List<Link> links) {

    public PersonalInfo {
        name = Checks.requireText(name, "name");
        headline = Checks.requireText(headline, "headline");
        summary = Checks.optionalText(summary);
        location = Checks.optionalText(location);
        email = Checks.optionalText(email);
        if (email != null && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new InvalidValueException("email", "is not a valid email address");
        }
        links = Checks.list(links, "links");
    }
}
