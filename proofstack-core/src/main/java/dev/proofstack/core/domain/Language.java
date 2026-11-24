package dev.proofstack.core.domain;

/** A spoken language and, optionally, how well: free text such as "Nativo", "C1" or "Professional working". */
public record Language(String name, String level) {

    public Language {
        name = Checks.requireText(name, "name");
        level = Checks.optionalText(level);
    }
}
