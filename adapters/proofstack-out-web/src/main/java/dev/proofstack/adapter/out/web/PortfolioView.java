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
        List<FeaturedView> featured) {
    public boolean hasFeatured() {
        return !featured.isEmpty();
    }
    /** A project shown without a click: what it is, what it proved, its stack and its links. */
    public record FeaturedView(String name, String summary, String result, List<String> techStack,
                               List<LinkView> links) {
        public boolean hasTechStack() {
            return !techStack.isEmpty();
        }
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
                             String email, List<LinkView> links, LinkView cv) {
        public boolean hasLinks() {
            return !links.isEmpty() || email != null || cv != null;
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

    public record ProjectView(String name, String summary, String problem, String solution, List<String> techStack,
                              List<String> learnings, List<LinkView> links, List<EvidenceView> evidence) {
        public boolean hasTechStack() {
            return !techStack.isEmpty();
        }

        public boolean hasLearnings() {
            return !learnings.isEmpty();
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
