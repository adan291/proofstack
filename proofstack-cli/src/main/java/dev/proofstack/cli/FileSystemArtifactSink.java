package dev.proofstack.cli;

import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.ArtifactSink;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes artifacts to disk, refusing any path that would resolve outside the output directory. */
public final class FileSystemArtifactSink implements ArtifactSink {

    @Override
    public void write(Path outputDirectory, List<Artifact> artifacts) {
        Path root = outputDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            for (Artifact artifact : artifacts) {
                Path target = root.resolve(artifact.relativePath()).normalize();
                if (!target.startsWith(root)) {
                    throw new IllegalArgumentException("Artifact escapes output directory: " + artifact.relativePath());
                }
                Files.createDirectories(target.getParent());
                Files.writeString(target, artifact.content(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write to " + root, e);
        }
    }
}
