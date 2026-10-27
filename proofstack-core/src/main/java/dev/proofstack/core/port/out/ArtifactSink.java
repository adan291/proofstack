package dev.proofstack.core.port.out;

import java.nio.file.Path;
import java.util.List;

/** Driven port: where generated artifacts are written. */
public interface ArtifactSink {

    void write(Path outputDirectory, List<Artifact> artifacts);
}
