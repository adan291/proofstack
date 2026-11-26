package dev.proofstack.adapter.out.cv;

import java.util.List;

/**
 * The CV as a neutral model, shared by the Markdown and the HTML output so both always agree.
 * The {@code has*} flags exist for the HTML template: a Mustache section over a list repeats its block per item.
 * They and {@code linked()} are called by name from the template, so an IDE may report them as unused.
 */
record Cv(CvText t, String name, String headline, List<Contact> contact, String summary,
          List<SkillGroup> skills, List<Job> jobs, List<Proj> projects, List<Study> studies,
          List<Cert> certifications, List<Lang> languages) {

    boolean hasContact() {
        return !contact.isEmpty();
    }

    boolean hasSkills() {
        return !skills.isEmpty();
    }

    boolean hasJobs() {
        return !mainJobs().isEmpty();
    }
    /**
     * Jobs that list technologies go under Experience. When some do and some do not, the rest (often work outside
     * software) is shown in one line each under Other experience, so it stays on the CV without taking the space.
     */
    List<Job> mainJobs() {
        return splitJobs() ? jobs.stream().filter(Job::technical).toList() : jobs;
    }
    List<Job> otherJobs() {
        return splitJobs() ? jobs.stream().filter(j -> !j.technical()).toList() : List.of();
    }
    boolean hasOtherJobs() {
        return !otherJobs().isEmpty();
    }
    private boolean splitJobs() {
        return jobs.stream().anyMatch(Job::technical) && !jobs.stream().allMatch(Job::technical);
    }

    boolean hasProjects() {
        return !projects.isEmpty();
    }

    boolean hasStudies() {
        return !studies.isEmpty();
    }

    boolean hasCertifications() {
        return !certifications.isEmpty();
    }

    boolean hasLanguages() {
        return !languages.isEmpty();
    }

    record Cert(String title, String detail, String url) {
        boolean linked() {
            return !url.isEmpty();
        }
    }

    record Lang(String name, String level) {
        /**
         * The level on a six-step scale (A1 = 1 ... C2 or native = 6), read from the level text the profile already has;
         * 0 when the text is not a level the CV can place. Drives the bar in the HTML CV, never adds words.
         */
        int bar() {
            String l = java.text.Normalizer.normalize(level, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT);
            if (l.matches(".*\\b(c2|nativ[oae]?|native|bilingue|bilingual)\\b.*")) {
                return 6;
            }
            var cefr = java.util.regex.Pattern.compile("\\b([abc])([12])\\b").matcher(l);
            if (cefr.find()) {
                return (cefr.group(1).charAt(0) - 'a') * 2 + cefr.group(2).charAt(0) - '0';
            }
            return l.matches(".*\\b(nociones|basico|basic|aprendizaje|learning)\\b.*") ? 1 : 0;
        }
        boolean hasBar() {
            return bar() > 0;
        }
    }

    record Contact(String text, String url) {
        boolean linked() {
            return !url.isEmpty();
        }
    }

    record SkillGroup(String area, List<String> items) {
        String names() {
            return String.join(", ", items);
        }
    }

    record Job(String role, String company, String location, String period, List<String> highlights,
               String technologies) {
        /** Location and period on one line; empty when both are. */
        String meta() {
            return location.isEmpty() || period.isEmpty() ? location + period : location + " · " + period;
        }

        boolean technical() {
            return !technologies.isEmpty();
        }

        /** "Role · Company (meta): highlights", for the one-line entries of Other experience. */
        String oneLine() {
            return role + " · " + company + (meta().isEmpty() ? "" : " (" + meta() + ")")
                    + (highlights.isEmpty() ? "" : ": " + String.join(" ", highlights));
        }

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
        String meta() {
            return period;
        }

        boolean hasHighlights() {
            return !highlights.isEmpty();
        }
    }
}
