package dev.proofstack.adapter.in.linkedin;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import dev.proofstack.adapter.in.linkedin.CsvParser.Row;
import dev.proofstack.core.domain.Education;
import dev.proofstack.core.domain.Language;
import dev.proofstack.core.domain.Certification;
import dev.proofstack.core.domain.Experience;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.Period;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Project;
import dev.proofstack.core.domain.Skill;
import dev.proofstack.core.domain.TodoCounter;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.port.out.ProfileImporter;

/**
 * Driven adapter: reads the official LinkedIn "Get a copy of your data" ZIP.
 *
 * <p>Security: entries are read in memory by file name only (no extraction to disk, so no
 * zip-slip), with a cap on the number of entries and on the bytes read per entry (zip bombs).
 * Email addresses and phone numbers are never imported (privacy by default).
 */
public final class LinkedInExportImporter implements ProfileImporter {

    static final int MAX_ENTRIES = 2000;
    static final int MAX_ENTRY_BYTES = 5 * 1024 * 1024;
    private static final String TODO = TodoCounter.MARKER;
    private static final Pattern URL = Pattern.compile("https?://[^\\s,\\]\\[]+");

    @Override
    public Profile importProfile(Path source) {
        Map<String, String> files = readCsvFiles(source);
        if (!files.containsKey("profile.csv") && !files.containsKey("positions.csv")) {
            throw new ProfileValidationException(new Violation("$",
                    "not a LinkedIn data export: Profile.csv / Positions.csv not found in " + source.getFileName()));
        }

        return fromTables(Map.of(
                "PROFILE", rows(files, "profile.csv"),
                "POSITIONS", rows(files, "positions.csv"),
                "EDUCATION", rows(files, "education.csv"),
                "PROJECTS", rows(files, "projects.csv"),
                "SKILLS", rows(files, "skills.csv"),
                "LANGUAGES", rows(files, "languages.csv"),
                "CERTIFICATIONS", rows(files, "certifications.csv")));
    }

    /**
     * Shared mapping for every LinkedIn source. The ZIP export (CSV files) and the Member Data
     * Portability API (snapshot domains) use the same column names, so both end up here.
     *
     * @param tables rows per snapshot domain: PROFILE, POSITIONS, EDUCATION, PROJECTS, SKILLS, LANGUAGES,
     *               CERTIFICATIONS
     */
    static Profile fromTables(Map<String, List<Row>> tables) {
        Ids ids = new Ids();
        PersonalInfo person = person(tables.getOrDefault("PROFILE", List.of()));
        List<Experience> experience = tables.getOrDefault("POSITIONS", List.of()).stream()
                .map(r -> experience(r, ids)).toList();
        List<Education> education = tables.getOrDefault("EDUCATION", List.of()).stream()
                .map(r -> education(r, ids)).toList();
        List<Project> projects = tables.getOrDefault("PROJECTS", List.of()).stream()
                .map(r -> project(r, ids)).toList();
        List<Skill> skills = skills(tables.getOrDefault("SKILLS", List.of()));

        List<Language> languages = tables.getOrDefault("LANGUAGES", List.of()).stream()
                .map(LinkedInExportImporter::language).flatMap(Optional::stream).toList();
        List<Certification> certifications = tables.getOrDefault("CERTIFICATIONS", List.of()).stream()
                .map(LinkedInExportImporter::certification).flatMap(Optional::stream).toList();
        return new Profile(Profile.DEFAULT_LANGUAGE, person, experience, education, projects, skills,
                List.of(), List.of(), languages, certifications);
    }

    // ---------------------------------------------------------------- mapping

    private static PersonalInfo person(List<Row> rows) {
        Row r = rows.isEmpty() ? new Row(Map.of()) : rows.getFirst();
        String name = (r.get("First Name") + " " + r.get("Last Name")).strip();
        String location = r.get("Geo Location");
        return new PersonalInfo(
                orTodo(name, "name"),
                orTodo(r.get("Headline"), "headline"),
                orTodo(r.get("Summary"), "summary"),
                location.isEmpty() ? null : location,
                null, // email: deliberately not imported
                websites(r.get("Websites")));
    }

    private static Experience experience(Row r, Ids ids) {
        String company = orTodo(r.get("Company Name"), "company");
        List<String> highlights = new ArrayList<>(lines(r.get("Description")));
        Period period = period(r.get("Started On"), r.get("Finished On"), highlights);
        if (highlights.isEmpty()) {
            highlights.add(TODO + ": add 1–3 concrete achievements");
        }
        String location = r.get("Location");
        return new Experience(ids.unique(company, "experience"), orTodo(r.get("Title"), "role"), company,
                location.isEmpty() ? null : location, period, highlights, List.of(), List.of());
    }

    private static Education education(Row r, Ids ids) {
        String school = orTodo(r.get("School Name"), "institution");
        List<String> highlights = new ArrayList<>(lines(r.get("Notes")));
        highlights.addAll(lines(r.get("Activities")));
        Period period = period(r.get("Start Date"), r.get("End Date"), highlights);
        return new Education(ids.unique(school, "education"), orTodo(r.get("Degree Name"), "title"), school,
                period, highlights);
    }

    private static Project project(Row r, Ids ids) {
        String title = orTodo(r.get("Title"), "project name");
        List<Link> links = new ArrayList<>();
        String url = r.get("Url");
        if (URL.matcher(url).matches()) {
            links.add(new Link("Link", url));
        }
        return new Project(ids.unique(title, "project"), title, orTodo(r.get("Description"), "summary"),
                TODO + ": what problem did it solve?", TODO + ": how did you solve it?",
                List.of(), List.of(), links, List.of(), List.of());
    }

    private static Optional<Language> language(Row r) {
        String name = r.get("Name");
        return name.isEmpty() ? Optional.empty() : Optional.of(new Language(name, r.get("Proficiency")));
    }

    private static Optional<Certification> certification(Row r) {
        String name = r.get("Name");
        if (name.isEmpty()) {
            return Optional.empty();
        }
        String url = r.get("Url");
        Link link = URL.matcher(url).matches() ? new Link("Credential", url) : null;
        return Optional.of(new Certification(name, r.get("Authority"),
                LinkedInDates.parse(r.get("Started On")).orElse(null), link));
    }

    private static List<Skill> skills(List<Row> rows) {
        Set<String> seen = new LinkedHashSet<>();
        List<Skill> skills = new ArrayList<>();
        for (Row r : rows) {
            String name = r.get("Name");
            if (!name.isEmpty() && seen.add(name.toLowerCase(Locale.ROOT))) {
                skills.add(new Skill(name, dev.proofstack.core.domain.AreaClassifier.classify(name)));
            }
        }
        return skills;
    }

    /** Missing start dates become a visible placeholder (see TodoCounter.MARKER) instead of an invented date. */
    private static Period period(String start, String end, List<String> highlights) {
        Optional<YearMonth> from = LinkedInDates.parse(start);
        Optional<YearMonth> to = LinkedInDates.parse(end);
        if (from.isEmpty()) {
            highlights.add(TODO + ": start date missing in LinkedIn, fix startDate");
            YearMonth placeholder = to.orElse(YearMonth.of(2000, 1));
            return new Period(placeholder, to.orElse(null));
        }
        if (to.isPresent() && to.get().isBefore(from.get())) {
            highlights.add(TODO + ": end date was before start date in LinkedIn, check dates");
            return new Period(from.get(), from.get());
        }
        return new Period(from.get(), to.orElse(null));
    }

    private static List<Link> websites(String value) {
        List<Link> links = new ArrayList<>();
        Matcher m = URL.matcher(value);
        while (m.find()) {
            String url = m.group();
            String host = Optional.ofNullable(URI.create(url).getHost()).orElse("Website").replaceFirst("^www\\.", "");
            links.add(new Link(host, url));
        }
        return links;
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String clean = line.strip().replaceFirst("^[-•*·–]\\s*", "").strip();
            if (!clean.isEmpty()) {
                lines.add(clean);
            }
        }
        return lines;
    }

    private static String orTodo(String value, String what) {
        return value == null || value.isBlank() ? TODO + ": " + what : value.strip();
    }

    // ---------------------------------------------------------------- zip

    private static List<Row> rows(Map<String, String> files, String name) {
        String content = files.get(name);
        return content == null ? List.of() : CsvParser.parse(content);
    }

    private static final Set<String> WANTED = Set.of("profile.csv", "positions.csv", "education.csv",
            "skills.csv", "projects.csv", "languages.csv", "certifications.csv");

    static Map<String, String> readCsvFiles(Path zip) {
        Map<String, String> files = new HashMap<>();
        try (ZipFile zipFile = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
            if (zipFile.size() > MAX_ENTRIES) {
                throw invalid("ZIP has " + zipFile.size() + " entries (limit " + MAX_ENTRIES + ")");
            }
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String fileName = entry.getName().replace('\\', '/');
                fileName = fileName.substring(fileName.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
                if (!WANTED.contains(fileName) || files.containsKey(fileName)) {
                    continue;
                }
                if (entry.getSize() > MAX_ENTRY_BYTES) {
                    throw invalid(entry.getName() + " is larger than " + (MAX_ENTRY_BYTES / 1024 / 1024) + " MiB");
                }
                try (InputStream in = zipFile.getInputStream(entry)) {
                    byte[] bytes = in.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (bytes.length > MAX_ENTRY_BYTES) {
                        throw invalid(entry.getName() + " expands beyond " + (MAX_ENTRY_BYTES / 1024 / 1024) + " MiB");
                    }
                    files.put(fileName, new String(bytes, StandardCharsets.UTF_8));
                }
            }
        } catch (ZipException e) {
            throw invalid("not a valid ZIP file: " + e.getMessage());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + zip, e);
        }
        return files;
    }

    private static ProfileValidationException invalid(String message) {
        return new ProfileValidationException(new Violation("$", message));
    }
}
