package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Project;

import java.util.List;

/** Driven port: external catalogue of projects (GitHub today, GitLab tomorrow). */
public interface ProjectSource {

    /**
     * @throws ImportException if the account does not exist or the source is unavailable
     */
    List<Project> fetchProjects(String account, boolean includeForks);

    /** Failure talking to an external source. */
    final class ImportException extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;

        public enum Kind { NOT_FOUND, RATE_LIMITED, UNAVAILABLE, INVALID_INPUT }

        private final Kind kind;

        public ImportException(String message, Kind kind) {
            super(message);
            this.kind = kind;
        }

        public ImportException(String message, Kind kind, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }
}
