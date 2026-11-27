package dev.proofstack.core.domain;

import java.util.List;

/**
 * Who the portfolio belongs to. Everything but name and headline is optional. {@code phone} is public: it is
 * only set when the person chose to publish it; {@code contactMessage} is their own text for the Contact section.
 */
public record PersonalInfo(
        String name,
        String headline,
        String summary,
        String location,
        String email,
        List<Link> links,
        String phone,
        String contactMessage) {

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
        phone = Checks.optionalText(phone);
        if (phone != null && !phone.matches("\\+?[0-9][0-9 ().-]{5,19}")) {
            throw new InvalidValueException("phone", "must contain only digits, spaces, +, -, . or parentheses");
        }
        contactMessage = Checks.optionalText(contactMessage);
    }

    /** Without a public phone or a contact message. */
    public PersonalInfo(String name, String headline, String summary, String location, String email, List<Link> links) {
        this(name, headline, summary, location, email, links, null, null);
    }
}
