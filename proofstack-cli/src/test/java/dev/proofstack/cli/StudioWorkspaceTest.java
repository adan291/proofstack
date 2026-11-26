package dev.proofstack.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudioWorkspaceTest {

    @TempDir
    Path repo;

    @Test
    void readsTheOriginOfTheRepositoryAndTheProfilePathFromItsRoot() throws IOException {
        Files.createDirectories(repo.resolve(".git"));
        Files.writeString(repo.resolve(".git/config"), """
                [core]
                \tbare = false
                [remote "upstream"]
                \turl = https://github.com/someone-else/proofstack.git
                [remote "origin"]
                \turl = git@github.com:jane-doe/my-portfolio.git
                """);
        Path profile = repo.resolve("profiles/jane/profile.yaml");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, "x");

        var w = StudioCommand.workspace(profile);

        assertThat(w.owner()).isEqualTo("jane-doe");
        assertThat(w.repo()).isEqualTo("my-portfolio");
        assertThat(w.profilePath()).isEqualTo("profiles/jane/profile.yaml");
    }

    @Test
    void withoutAGitHubOriginOnlyThePathIsKnown() {
        var w = StudioCommand.fromGitConfig("[remote \"origin\"]\n\turl = https://gitlab.com/jane/site.git\n", "p.yaml");
        assertThat(w.owner()).isNull();
        assertThat(w.profilePath()).isEqualTo("p.yaml");
        assertThat(StudioCommand.fromGitConfig("[remote \"origin\"]\n\turl = https://github.com/jane/site\n", "p")
                .repo()).isEqualTo("site");
    }
}
