package dev.proofstack.adapter.out.cv;

import java.util.List;

/**
 * The CV as a neutral model, shared by the Markdown and the HTML output so both always agree.
 * The {@code has*} flags exist for the HTML template: a Mustache section over a list repeats its block per item.
 */
record Cv(CvText t, String name, String headline, List<Contact> contact, String summary,
          List<SkillGroup> skills, List<Job> jobs, List<Proj> projects, List<Study> studies) {

    boolean hasContact() {
        return !contact.isEmpty();
    }

    boolean hasSkills() {
        return !skills.isEmpty();
    }

    boolean hasJobs() {
        return !jobs.isEmpty();
    }

    boolean hasProjects() {
        return !projects.isEmpty();
    }

    boolean hasStudies() {
        return !studies.isEmpty();
    }

    record Contact(String text, String url) {
        boolean linked() {
            return !url.isEmpty();
        }
    }

    record SkillGroup(String area, String names) {
    }

    record Job(String role, String company, String location, String period, List<String> highlights,
               String technologies) {
        boolean hasHighlights() {
            return !highlights.isEmpty();
        }
    }

    record Proj(String name, String summary, String stack, String result, String linkLabel, String linkUrl) {
        boolean hasLink() {
            return !linkUrl.isEmpty();
        }
    }

    record Study(String title, String institution, String period, List<String> highlights) {
        boolean hasHighlights() {
            return !highlights.isEmpty();
        }
    }
}
