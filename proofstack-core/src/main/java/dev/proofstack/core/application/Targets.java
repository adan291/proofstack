package dev.proofstack.core.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Importers never modify the profile they read: the target must be another file, not another name for it. */
final class Targets {

    private Targets() {
    }

    static void requireDifferent(Path original, Path target) {
        boolean same = original.toAbsolutePath().normalize().equals(target.toAbsolutePath().normalize());
        if (!same && Files.exists(target)) {
            try {
                same = Files.isSameFile(original, target); // hard link, symlink, letter case on macOS...
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot compare " + original + " and " + target, e);
            }
        }
        if (same) {
            throw new IllegalArgumentException("--out must be a different file: the original profile is never modified");
        }
    }
}
