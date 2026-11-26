package dev.proofstack.core.port.in;

import java.util.List;

import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProjectSource;

/** Driving port: LinkedIn or GitHub merged into an unsaved draft of the profile. Nothing is written. */
public interface ImportIntoDraftUseCase {

    /** The draft with its career (experience, education, skills, languages...) refreshed from the member's profile. */
    Imported career(String draftJson, MemberProfileSource source);

    /** The draft plus the account's public repositories that are not in it yet, as draft projects. */
    Imported projects(String draftJson, ProjectSource source, String account);

    /**
     * @param json    the draft with the import merged in, ready for the editor
     * @param added   what is new, in the reader's words ("Developer @ Acme", a repository id); empty when nothing changed
     * @param leftOut jobs the draft has and the import does not, kept as they were
     */
    record Imported(String json, List<String> added, List<String> leftOut, int todoCount) {
        public Imported {
            added = List.copyOf(added);
            leftOut = List.copyOf(leftOut);
        }
    }
}