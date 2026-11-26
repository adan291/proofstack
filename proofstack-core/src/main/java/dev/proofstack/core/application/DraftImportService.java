package dev.proofstack.core.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileMerge;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.port.in.ImportIntoDraftUseCase;
import dev.proofstack.core.port.out.MemberProfileSource;
import dev.proofstack.core.port.out.ProfileJsonCodec;
import dev.proofstack.core.port.out.ProjectSource;

/**
 * Brings LinkedIn or GitHub data into the editor's unsaved draft. Same merge rules as the {@code import}
 * commands ({@link ProfileMerge}, {@link ProjectMergeService}), but nothing is written: the editor shows the
 * result and the user saves it, or not.
 */
public final class DraftImportService implements ImportIntoDraftUseCase {

    private final ProfileJsonCodec codec;

    public DraftImportService(ProfileJsonCodec codec) {
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public Imported career(String draftJson, MemberProfileSource source) {
        Profile draft = codec.parse(draftJson); // fail before the login if the draft cannot be merged
        ProfileMerge.Result merged = ProfileMerge.merge(draft, source.fetchProfile());
        Profile result = merged.profile();
        List<String> added = new ArrayList<>();
        Set<String> jobs = draft.experience().stream().map(DraftImportService::job).collect(Collectors.toSet());
        result.experience().stream().map(DraftImportService::job).filter(j -> !jobs.contains(j)).forEach(added::add);
        Set<String> studies = draft.education().stream().map(DraftImportService::study).collect(Collectors.toSet());
        result.education().stream().map(DraftImportService::study).filter(s -> !studies.contains(s)).forEach(added::add);
        Set<String> skills = draft.skills().stream().map(s -> s.name().toLowerCase(java.util.Locale.ROOT))
                .collect(Collectors.toSet());
        result.skills().stream().filter(s -> !skills.contains(s.name().toLowerCase(java.util.Locale.ROOT)))
                .forEach(s -> added.add(s.name()));
        List<String> leftOut = merged.notInImport().stream().map(DraftImportService::job).toList();
        return new Imported(codec.toJson(result), added, leftOut, TodoCounter.count(result));
    }

    @Override
    public Imported projects(String draftJson, ProjectSource source, String account) {
        Profile draft = codec.parse(draftJson);
        ProjectMergeService.Merged merged = ProjectMergeService.mergeProjects(draft, source.fetchProjects(account, false));
        return new Imported(codec.toJson(merged.profile()), merged.added(), List.of(), TodoCounter.count(merged.profile()));
    }

    private static String job(Experience e) {
        return e.role() + " @ " + e.company();
    }

    private static String study(Education e) {
        return e.title() + " @ " + e.institution();
    }
}
