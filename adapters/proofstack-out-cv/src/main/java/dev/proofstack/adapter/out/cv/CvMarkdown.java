package dev.proofstack.adapter.out.cv;

import java.util.List;
import java.util.stream.Collectors;

import dev.proofstack.adapter.out.cv.Cv.Contact;

/** Plain Markdown: easy to edit by hand and to paste into any editor or applicant form. */
final class CvMarkdown {

    private CvMarkdown() {
    }

    static String render(Cv cv) {
        CvText t = cv.t();
        StringBuilder md = new StringBuilder();
        md.append("# ").append(cv.name()).append("\n\n").append(cv.headline()).append("\n\n");
        if (!cv.contact().isEmpty()) {
            md.append(cv.contact().stream().map(CvMarkdown::contact).collect(Collectors.joining(" · "))).append("\n\n");
        }
        if (!cv.summary().isEmpty()) {
            section(md, t.profile());
            md.append(cv.summary()).append("\n\n");
        }
        if (!cv.skills().isEmpty()) {
            section(md, t.skills());
            cv.skills().forEach(g -> md.append("- **").append(g.area()).append(":** ").append(g.names()).append('\n'));
            md.append('\n');
        }
        if (!cv.mainJobs().isEmpty()) {
            section(md, t.experience());
            cv.mainJobs().forEach(job -> {
                md.append("### ").append(job.role()).append(" · ").append(job.company()).append("\n\n");
                meta(md, job.meta());
                bullets(md, job.highlights());
                if (!job.technologies().isEmpty()) {
                    md.append(t.technologies()).append(": ").append(job.technologies()).append("\n\n");
                }
            });
        }
        if (cv.hasOtherJobs()) {
            section(md, t.otherExperience());
            bullets(md, cv.otherJobs().stream().map(Cv.Job::oneLine).toList());
        }
        if (!cv.projects().isEmpty()) {
            section(md, t.projects());
            cv.projects().forEach(p -> {
                md.append("### ").append(p.name()).append("\n\n").append(p.summary()).append("\n\n");
                if (!p.stack().isEmpty()) {
                    md.append("- ").append(t.stack()).append(": ").append(p.stack()).append('\n');
                }
                if (!p.result().isEmpty()) {
                    md.append("- ").append(t.result()).append(": ").append(p.result()).append('\n');
                }
                if (p.hasLink()) {
                    md.append("- [").append(p.linkLabel().isEmpty() ? p.linkUrl() : p.linkLabel()).append("](")
                            .append(p.linkUrl()).append(")\n");
                }
                md.append('\n');
            });
        }
        if (!cv.studies().isEmpty()) {
            section(md, t.education());
            cv.studies().forEach(s -> {
                md.append("### ").append(s.title()).append(" · ").append(s.institution()).append("\n\n");
                meta(md, s.meta());
                bullets(md, s.highlights());
            });
        }
        if (!cv.certifications().isEmpty()) {
            section(md, t.certifications());
            cv.certifications().forEach(c -> {
                md.append("- ").append(c.linked() ? "[" + c.title() + "](" + c.url() + ")" : c.title());
                if (!c.detail().isEmpty()) {
                    md.append(" — ").append(c.detail());
                }
                md.append('\n');
            });
            md.append('\n');
        }
        if (!cv.languages().isEmpty()) {
            section(md, t.languages());
            cv.languages().forEach(l -> md.append("- ").append(l.name())
                    .append(l.level().isEmpty() ? "" : " (" + l.level() + ")").append('\n'));
            md.append('\n');
        }
        return md.toString().stripTrailing() + "\n";
    }

    private static String contact(Contact c) {
        return c.linked() ? "[" + c.text() + "](" + c.url() + ")" : c.text();
    }

    private static void section(StringBuilder md, String title) {
        md.append("## ").append(title).append("\n\n");
    }

    private static void bullets(StringBuilder md, List<String> items) {
        if (!items.isEmpty()) {
            items.forEach(item -> md.append("- ").append(item).append('\n'));
            md.append('\n');
        }
    }

    private static void meta(StringBuilder md, String meta) {
        if (!meta.isEmpty()) {
            md.append('*').append(meta).append("*\n\n");
        }
    }
}
