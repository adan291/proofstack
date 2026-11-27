package dev.proofstack.adapter.yaml;

import dev.proofstack.adapter.yaml.ProfileDocument.CertificationDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.EducationDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.EvidenceDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.ExperienceDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.LabDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.LinkDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.ProjectDoc;
import dev.proofstack.adapter.yaml.ProfileDocument.SkillDoc;
import dev.proofstack.core.domain.Area;
import dev.proofstack.core.domain.Certification;
import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Evidence;
import dev.proofstack.core.domain.EvidenceCategory;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.InvalidValueException;
import dev.proofstack.core.domain.Lab;
import dev.proofstack.core.domain.Language;
import dev.proofstack.core.domain.LabType;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileDate;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.Violation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Maps the (already schema-valid) document to the domain, translating domain
 * invariant failures into located {@link Violation}s.
 */
final class DocumentMapper {

    private final List<Violation> violations = new ArrayList<>();

    Profile map(ProfileDocument doc) {
        PersonalInfo person = attempt("$.person", () -> new PersonalInfo(
                doc.person().name(), doc.person().headline(), doc.person().summary(),
                doc.person().location(), doc.person().email(), links(doc.person().links()),
                doc.person().phone(), doc.person().contactMessage()));

        List<Skill> skills = each("$.skills", doc.skills(), this::skill);
        List<Experience> experience = each("$.experience", doc.experience(), this::experience);
        List<Education> education = each("$.education", doc.education(), this::education);
        List<Project> projects = each("$.projects", doc.projects(), this::project);
        List<Evidence> evidence = each("$.evidence", doc.evidence(), this::evidence);
        List<Lab> labs = each("$.labs", doc.labs(), this::lab);
        List<Language> languages = each("$.languages", doc.languages(), d -> new Language(d.name(), d.level()));
        List<Certification> certifications = each("$.certifications", doc.certifications(), this::certification);

        if (!violations.isEmpty()) {
            throw new ProfileValidationException(violations);
        }
        // Cross-entity invariants (duplicate ids, dangling references) are checked by the aggregate itself.
        return new Profile(doc.language(), person, experience, education, projects, skills, evidence, labs,
                languages, certifications);
    }

    private Skill skill(SkillDoc d) {
        return new Skill(d.name(), area(d.area()));
    }

    private Experience experience(ExperienceDoc d) {
        if (Boolean.TRUE.equals(d.current()) && d.endDate() != null) {
            throw new InvalidValueException("endDate", "must be omitted when current is true");
        }
        return new Experience(d.id(), d.role(), d.company(), d.location(), period(d.startDate(), d.endDate()),
                d.highlights(), d.technologies(), d.evidence());
    }

    private Education education(EducationDoc d) {
        return new Education(d.id(), d.title(), d.institution(), period(d.startDate(), d.endDate()), d.highlights());
    }

    private Project project(ProjectDoc d) {
        return new Project(d.id(), d.name(), d.summary(), d.problem(), d.solution(), d.techStack(), d.learnings(),
                links(d.links()), areas(d.areas()), d.evidence(), Boolean.TRUE.equals(d.featured()),
                areaNotes(d.areaNotes()));
    }

    private Evidence evidence(EvidenceDoc d) {
        Link link = d.link() == null ? null : new Link(d.link().label(), d.link().url());
        return new Evidence(d.id(), enumValue(EvidenceCategory.class, d.category(), "category"), d.title(),
                d.context(), d.action(), d.result(), link, areas(d.areas()));
    }

    private Certification certification(CertificationDoc d) {
        Link link = d.link() == null ? null : new Link(d.link().label(), d.link().url());
        return new Certification(d.title(), d.issuer(), d.date() == null ? null : date(d.date(), "date"), link);
    }

    private Lab lab(LabDoc d) {
        return new Lab(d.id(), d.name(), enumValue(LabType.class, d.type(), "type"), d.description(), d.projects());
    }

    private static List<Link> links(List<LinkDoc> docs) {
        if (docs == null) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            try {
                links.add(new Link(docs.get(i).label(), docs.get(i).url()));
            } catch (InvalidValueException e) {
                throw new InvalidValueException("links[" + i + "]." + e.field(), e.reason());
            }
        }
        return links;
    }

    private static List<Area> areas(List<String> values) {
        return values == null ? List.of() : values.stream().map(DocumentMapper::area).toList();
    }

    private static java.util.Map<Area, String> areaNotes(java.util.Map<String, String> values) {
        java.util.Map<Area, String> notes = new java.util.EnumMap<>(Area.class);
        if (values != null) {
            values.forEach((area, note) -> notes.put(area(area), note));
        }
        return notes;
    }

    private static Area area(String value) {
        return enumValue(Area.class, value, "area");
    }

    private static Period period(String start, String end) {
        return new Period(date(start, "startDate"), end == null ? null : date(end, "endDate"));
    }

    /** {@code 2024}, {@code 2024-03}, or the integer a YAML reader makes of an unquoted year. */
    private static ProfileDate date(String value, String field) {
        try {
            return value == null ? null : ProfileDate.parse(value);
        } catch (InvalidValueException e) {
            throw new InvalidValueException(field, e.reason());
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new InvalidValueException(field, "has an unknown value '" + value + "'");
        }
    }

    private <D, T> List<T> each(String path, List<D> docs, Function<D, T> mapper) {
        if (docs == null) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            D doc = docs.get(i);
            T value = attempt(path + "[" + i + "]", () -> mapper.apply(doc));
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private <T> T attempt(String path, java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (InvalidValueException e) {
            violations.add(new Violation(path + "." + e.field(), e.reason()));
            return null;
        }
    }
}
