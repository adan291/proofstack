package dev.proofstack.adapter.in.studio;

import java.util.Objects;
import java.util.function.Supplier;

import dev.proofstack.core.port.in.ImportIntoDraftUseCase;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProjectSource;

/**
 * What the "Conectar LinkedIn" and "Traer repos de GitHub" buttons use. Wired by the CLI, which knows the adapters.
 *
 * @param linkedIn a new LinkedIn source per import: it runs the OAuth login. It throws
 *                 {@link IllegalStateException} with a message for the user when LinkedIn is not configured yet
 */
public record StudioImports(ImportIntoDraftUseCase drafts, Supplier<MemberProfileSource> linkedIn, ProjectSource gitHub) {
    public StudioImports {
        Objects.requireNonNull(drafts, "drafts");
        Objects.requireNonNull(linkedIn, "linkedIn");
        Objects.requireNonNull(gitHub, "gitHub");
    }
}
