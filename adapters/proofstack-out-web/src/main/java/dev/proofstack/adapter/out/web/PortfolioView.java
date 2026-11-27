package dev.proofstack.adapter.out.web;

import java.util.List;

/**
 * Presentation model consumed by the Mustache templates. Templates are logic-less, so
 * every conditional the page needs is precomputed here as a boolean.
 */
public record PortfolioView(
        String lang,
        UiText t,
        String csp,
        AssistantView assistant,
        PersonView person,
        List<NodeView> nodes,
        List<ExperienceView> experience,
        List<EducationView> education,
        List<FeaturedView> featured,
        List<AlternateView> alternates,
        String currentRole,
        List<CertificationView> certifications) {
    /** A certification: its title, who issued it and when, and where to verify it (empty when there is no link). */
    public record CertificationView(String title, String detail, String url) {
        public boolean hasDetail() {
            return !detail.isBlank();
        }

        public boolean hasUrl() {
            return !url.isBlank();
        }
    }

    public boolean hasCertifications() {
        return !certifications.isEmpty();
    }

    /** Studies and certifications share the Education section; it shows when either is present. */
    public boolean hasEducationSection() {
        return hasEducation() || hasCertifications();
    }

    /** The same portfolio in another language. */
    public record AlternateView(String lang, String name, String href) {
    }

    public boolean hasAlternates() {
        return !alternates.isEmpty();
    }

    public boolean hasFeatured() {
        return !featured.isEmpty();
    }
    /**
     * A project shown without a click: its name, one short description and where to go next. The full card
     * (result, stack, evidence) lives in the area panel that {@code detailHref} points to, so nothing is repeated.
     */
    public record FeaturedView(String name, String summary, List<LinkView> links, String detailHref) {
        public boolean hasLinks() {
            return !links.isEmpty();
        }
    }

    public boolean hasNodes() {
        return !nodes.isEmpty();
    }

    public boolean hasExperience() {
        return !experience.isEmpty();
    }

    /**
     * Same rule as the CV: jobs that list technologies are the main experience. When only some do, the rest
     * (often work outside software) moves to a compact "other experience" list.
     */
    public List<ExperienceView> mainExperience() {
        return splitExperience() ? experience.stream().filter(ExperienceView::hasTechnologies).toList() : experience;
    }

    public List<ExperienceView> otherExperience() {
        return splitExperience() ? experience.stream().filter(e -> !e.hasTechnologies()).toList() : List.of();
    }

    public boolean hasOtherExperience() {
        return !otherExperience().isEmpty();
    }

    private boolean splitExperience() {
        return experience.stream().anyMatch(ExperienceView::hasTechnologies)
                && !experience.stream().allMatch(ExperienceView::hasTechnologies);
    }

    public boolean hasEducation() {
        return !education.isEmpty();
    }

    public record LinkView(String label, String url) {
    }

    /** Present only when the site is built with an assistant URL. */
    public record AssistantView(String endpoint) {
    }

    /** Link to the CV published next to the site, or {@code null} when the site is built without it. */
    public record PersonView(String name, String initials, String headline, String summary, String location,
                             String email, List<LinkView> links, LinkView cv, String phone, String phoneHref,
                             String contactMessage) {
        public boolean hasLinks() {
            return !links.isEmpty() || email != null || cv != null || phone != null;
        }
    }

    public record NodeView(String key, String label, String stats, List<String> skills,
                           List<ProjectView> projects, List<EvidenceView> evidence, List<LabView> labs) {
        public String buttonId() {
            return "node-" + key;
        }

        public String panelId() {
            return "panel-" + key;
        }

        public boolean hasSkills() {
            return !skills.isEmpty();
        }

        public boolean hasProjects() {
            return !projects.isEmpty();
        }

        public boolean hasEvidence() {
            return !evidence.isEmpty();
        }

        public boolean hasLabs() {
            return !labs.isEmpty();
        }
    }

    public record ProjectView(String anchor, String name, String summary, String result, String problem,
                              String solution, List<String> techStack, List<String> learnings, List<LinkView> links,
                              List<EvidenceView> evidence) {
        public boolean hasResult() {
            return result != null && !result.isBlank();
        }

        public boolean hasTechStack() {
            return !techStack.isEmpty();
        }

        public boolean hasLearnings() {
            return !learnings.isEmpty();
        }

        /** Problem, solution and learnings go behind a disclosure so the grid shows what each project is first. */
        public boolean hasDetails() {
            return (problem != null && !problem.isBlank()) || (solution != null && !solution.isBlank()) || hasLearnings();
        }

        public boolean hasLinks() {
            return !links.isEmpty();
        }

        public boolean hasEvidence() {
            return !evidence.isEmpty();
        }
    }

    public record EvidenceView(String title, String category, String context, String action, String result,
                               LinkView link) {
    }

    public record LabView(String name, String type, String description) {
    }

    public record ExperienceView(String role, String company, String location, String period,
                                 List<String> highlights, List<String> technologies) {
        public boolean hasHighlights() {
            return !highlights.isEmpty();
        }

        public boolean hasTechnologies() {
            return !technologies.isEmpty();
        }
    }

    public record EducationView(String title, String institution, String period, List<String> highlights) {
        public boolean hasHighlights() {
            return !highlights.isEmpty();
        }
    }
}
