package dev.proofstack.core.port.in;

import java.nio.file.Path;
import java.util.List;

/** Driving port: add external projects to a copy of the profile without touching the original. */
public interface MergeProjectsUseCase {

    MergeReport merge(Path profile, String account, boolean includeForks, Path target, boolean overwrite);

    /**
     * @param added   ids of the projects added
     * @param skipped ids of external projects already present (same id or same URL)
     */
    record MergeReport(List<String> added, List<String> skipped, int todoCount) {
        public MergeReport {
            added = List.copyOf(added);
            skipped = List.copyOf(skipped);
        }
    }
}
