package dev.proofstack.adapter.in.yaml;

import java.util.List;

/**
 * Wire format of profile.yaml. Kept package-private and separate from the domain so the
 * document can evolve (versions, aliases) without touching the core.
 */
record ProfileDocument(
        String language,
        PersonDoc person,
        List<SkillDoc> skills,
        List<ExperienceDoc> experience,
        List<EducationDoc> education,
        List<ProjectDoc> projects,
        List<EvidenceDoc> evidence,
        List<LabDoc> labs,
        List<LanguageDoc> languages,
        List<CertificationDoc> certifications) {

    record LinkDoc(String label, String url) {
    }

    record PersonDoc(String name, String headline, String summary, String location, String email,
                     List<LinkDoc> links) {
    }

    record SkillDoc(String name, String area) {
    }

    record ExperienceDoc(String id, String role, String company, String location, String startDate,
                         String endDate, Boolean current, List<String> highlights, List<String> technologies,
                         List<String> evidence) {
    }

    record EducationDoc(String id, String title, String institution, String startDate, String endDate,
                        List<String> highlights) {
    }

    record ProjectDoc(String id, String name, String summary, String problem, String solution,
                      List<String> techStack, List<String> learnings, List<LinkDoc> links, List<String> areas,
                      List<String> evidence) {
    }

    record EvidenceDoc(String id, String category, String title, String context, String action, String result,
                       LinkDoc link, List<String> areas) {
    }

    record LanguageDoc(String name, String level) {
    }

    record CertificationDoc(String title, String issuer, String date, LinkDoc link) {
    }

    record LabDoc(String id, String name, String type, String description, List<String> projects) {
    }
}
