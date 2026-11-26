package dev.proofstack.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import dev.proofstack.core.port.out.ProjectSource;

/**
 * {@code github-ignore.txt}, next to the profile: repositories the user chose not to show, one name per line.
 * Every GitHub import skips them, so a repository that was turned down once does not come back every week.
 */
final class GitHubIgnore {

    static final String FILE = "github-ignore.txt";

    private GitHubIgnore() {
    }

    static Path fileFor(Path profile) {
        return profile.toAbsolutePath().normalize().resolveSibling(FILE);
    }

    static Set<String> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Set.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#"))
                    .map(l -> l.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /** Appends the names that are not there yet; creates the file with an explanation the first time. */
    static void add(Path file, Collection<String> names) {
        Set<String> known = read(file);
        Set<String> fresh = new LinkedHashSet<>();
        names.stream().map(String::strip).filter(n -> !n.isEmpty() && !n.contains("\n"))
                .filter(n -> !known.contains(n.toLowerCase(Locale.ROOT))).forEach(fresh::add);
        if (fresh.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        if (!Files.exists(file)) {
            text.append("# GitHub repositories that never go into the portfolio, one per line.\n")
                    .append("# Delete a line to be offered that repository again.\n");
        }
        fresh.forEach(n -> text.append(n).append('\n'));
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    /** The source without the ignored repositories (matched by repository name, case-insensitive). */
    static ProjectSource filtered(ProjectSource source, Path file) {
        return (account, includeForks) -> {
            Set<String> ignored = read(file);
            return source.fetchProjects(account, includeForks).stream()
                    .filter(p -> !ignored.contains(p.name().toLowerCase(Locale.ROOT))).toList();
        };
    }
}
