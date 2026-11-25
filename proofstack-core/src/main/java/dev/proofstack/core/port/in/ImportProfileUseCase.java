package dev.proofstack.core.port.in;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Driving port: bootstrap a profile.yaml from an external source. */
public interface ImportProfileUseCase {

    ImportReport importProfile(Path source, Path target, boolean overwrite);

    /**
     * @param todoCount fields that the user must complete before publishing
     * @param counts    imported items per section, e.g. {@code experience=3}
     * @param leftOut   jobs of the existing profile that the source does not list and are not in the result,
     *                  described for the user, e.g. {@code "Developer @ Freelance (evidence: shop)"}
     */
    record ImportReport(int todoCount, Map<String, Integer> counts, List<String> leftOut) {
        public ImportReport {
            counts = Map.copyOf(counts);
            leftOut = List.copyOf(leftOut);
        }

        public ImportReport(int todoCount, Map<String, Integer> counts) {
            this(todoCount, counts, List.of());
        }
    }
}
