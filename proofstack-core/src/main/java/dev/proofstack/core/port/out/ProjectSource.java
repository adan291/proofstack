package dev.proofstack.core.port.out;

import dev.proofstack.core.domain.Project;

import java.util.List;

/** Driven port: external catalogue of projects (GitHub today, GitLab tomorrow). */
public interface ProjectSource {

    /**
     * @throws ImportException if the account does not exist or the source is unavailable
     */
    List<Project> fetchProjects(String account, boolean includeForks);

}
