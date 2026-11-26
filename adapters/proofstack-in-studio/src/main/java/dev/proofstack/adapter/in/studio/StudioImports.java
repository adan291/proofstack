package dev.proofstack.adapter.in.studio;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.proofstack.core.port.in.ImportIntoDraftUseCase;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProjectSource;

/**
 * What the import buttons and the setup assistant use. Wired by the CLI, which knows the adapters and the files.
 *
 * @param linkedIn           a new LinkedIn source per import: it runs the OAuth login. It throws
 *                           {@link IllegalStateException} with a message for the user when LinkedIn is not set up yet
 * @param linkedInConfigured whether a LinkedIn app (or token) is available, so the assistant knows which step to show
 * @param saveLinkedInApp    saves the user's LinkedIn app (client id, client secret) outside the repository
 * @param ignoreRepos        appends repository names to the list that GitHub imports skip from now on
 * @param workspace          where the profile lives, for the publishing step; may return {@code null} fields
 */
public record StudioImports(ImportIntoDraftUseCase drafts, Supplier<MemberProfileSource> linkedIn, ProjectSource gitHub,
                            BooleanSupplier linkedInConfigured, BiConsumer<String, String> saveLinkedInApp,
                            Consumer<List<String>> ignoreRepos, Supplier<Workspace> workspace) {

    /**
     * @param owner       GitHub owner of the repository (from {@code remote.origin.url}), or {@code null}
     * @param repo        repository name, or {@code null}
     * @param profilePath the profile relative to the repository root, as {@code PROOFSTACK_PROFILE} expects it
     */
    public record Workspace(String owner, String repo, String profilePath) {
        public static final Workspace UNKNOWN = new Workspace(null, null, null);
    }

    public StudioImports {
        Objects.requireNonNull(drafts, "drafts");
        Objects.requireNonNull(linkedIn, "linkedIn");
        Objects.requireNonNull(gitHub, "gitHub");
        Objects.requireNonNull(linkedInConfigured, "linkedInConfigured");
        Objects.requireNonNull(saveLinkedInApp, "saveLinkedInApp");
        Objects.requireNonNull(ignoreRepos, "ignoreRepos");
        Objects.requireNonNull(workspace, "workspace");
    }

    /** Only the import buttons: no LinkedIn app to save, nothing ignored, no workspace. */
    public StudioImports(ImportIntoDraftUseCase drafts, Supplier<MemberProfileSource> linkedIn, ProjectSource gitHub) {
        this(drafts, linkedIn, gitHub, () -> false, (id, secret) -> {
            throw new IllegalStateException("saving a LinkedIn app is not available here");
        }, names -> { }, () -> Workspace.UNKNOWN);
    }
}
