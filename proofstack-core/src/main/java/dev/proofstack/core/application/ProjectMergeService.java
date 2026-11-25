package dev.proofstack.core.application;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.port.in.MergeProjectsUseCase;
import dev.proofstack.core.port.out.ProfileSource;
import dev.proofstack.core.port.out.ProfileWriter;
import dev.proofstack.core.port.out.ProjectSource;

/**
 * Merges external projects into a profile. Existing projects always win: an external project is
 * skipped if its id or any of its URLs is already in the profile. The result goes to a new file.
 */
public final class ProjectMergeService implements MergeProjectsUseCase {

    private final ProfileSource profiles;
    private final ProjectSource projects;
    private final ProfileWriter writer;

    public ProjectMergeService(ProfileSource profiles, ProjectSource projects, ProfileWriter writer) {
        this.profiles = Objects.requireNonNull(profiles, "profiles");
        this.projects = Objects.requireNonNull(projects, "projects");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    @Override
    public MergeReport merge(Path profilePath, String account, boolean includeForks, Path target, boolean overwrite) {
        Targets.requireDifferent(profilePath, target);
        Profile profile = profiles.load(profilePath);
        Set<String> ids = new HashSet<>();
        Set<String> urls = new HashSet<>();
        for (Project p : profile.projects()) {
            ids.add(p.id());
            p.links().stream().map(Link::url).map(ProjectMergeService::normalizeUrl).forEach(urls::add);
        }

        List<Project> merged = new ArrayList<>(profile.projects());
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Project candidate : projects.fetchProjects(account, includeForks)) {
            boolean known = ids.contains(candidate.id()) || candidate.links().stream()
                    .map(Link::url).map(ProjectMergeService::normalizeUrl).anyMatch(urls::contains);
            if (known) {
                skipped.add(candidate.id());
            } else {
                merged.add(candidate);
                added.add(candidate.id());
                ids.add(candidate.id());
            }
        }

        Profile result = new Profile(profile.language(), profile.person(), profile.experience(), profile.education(),
                merged, profile.skills(), profile.evidence(), profile.labs(), profile.languages(),
                profile.certifications());
        writer.write(result, target, overwrite);
        return new MergeReport(added, skipped, TodoCounter.count(result));
    }

    private static String normalizeUrl(String url) {
        return url.toLowerCase(Locale.ROOT).replaceFirst("^http://", "https://").replaceAll("(\\.git)?/*$", "");
    }
}
