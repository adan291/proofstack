package dev.proofstack.core.port.in;

import java.nio.file.Path;
import java.util.Map;

/** Driving port: bootstrap a profile.yaml from an external source. */
public interface ImportProfileUseCase {

    ImportReport importProfile(Path source, Path target, boolean overwrite);

    /**
     * @param todoCount fields that the user must complete before publishing
     * @param counts    imported items per section, e.g. {@code experience=3}
     */
    record ImportReport(int todoCount, Map<String, Integer> counts) {
        public ImportReport {
            counts = Map.copyOf(counts);
        }
    }
}
