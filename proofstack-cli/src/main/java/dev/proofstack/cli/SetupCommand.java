package dev.proofstack.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import dev.proofstack.adapter.in.linkedin.LinkedInSettings;
import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.adapter.yaml.YamlProfileWriter;
import dev.proofstack.core.domain.Link;
import dev.proofstack.core.domain.PersonalInfo;
import dev.proofstack.core.domain.Profile;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * {@code proofstack setup}: everything a new user needs, in one command. Creates the profile if there is none, links
 * GitHub and brings its repositories, sets up LinkedIn once and imports it, then opens Studio on what is left.
 * Each step is the same code as its own command; a step that fails is reported and the rest go on.
 */
@Command(name = "setup", description = {"Start here: create or update your profile from GitHub and LinkedIn, then edit it.",
        "Asks only for what it cannot find. Safe to run again: existing projects and evidence are never touched."})
final class SetupCommand implements Callable<Integer> {

    /** Where answers come from. Package-private so tests can answer the questions. */
    static BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @ParentCommand
    ProofstackCli parent;

    @Parameters(index = "0", arity = "0..1", paramLabel = "DIR",
            description = "Folder of your profile, e.g. profiles/ada (default: profiles/<your-name>)")
    Path dir;

    @Option(names = {"-n", "--name"}, description = "Your name (asked if missing)")
    String name;

    @Option(names = {"-t", "--headline"}, description = "Your role (default: Backend Developer)")
    String headline;

    @Option(names = {"-l", "--language"}, defaultValue = "es", description = "es or en (default: ${DEFAULT-VALUE})")
    String language;

    @Option(names = {"-g", "--github"}, paramLabel = "USER", description = "Your GitHub user (asked if missing)")
    String github;

    @Option(names = "--linkedin-zip", paramLabel = "ZIP", description = "Use this LinkedIn data export instead of the API")
    Path linkedInZip;

    @Option(names = "--skip-linkedin", description = "Do not import LinkedIn this time")
    boolean skipLinkedIn;

    @Option(names = "--no-studio", description = "Do not open Studio at the end")
    boolean noStudio;

    @Option(names = {"-y", "--yes"}, description = "Do not ask anything: use the options and the defaults")
    boolean yes;

    @Spec
    CommandLine.Model.CommandSpec spec;

    private PrintWriter out;
    private final List<String> problems = new ArrayList<>();

    @Override
    public Integer call() {
        out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!language.equals("es") && !language.equals("en")) {
            err.println("Unsupported language '" + language + "'. Use es or en.");
            return ProofstackCli.INVALID;
        }
        if (github != null && !github.isBlank() && !validGitHubUser(github.strip())) {
            err.println("Not a GitHub user name: " + github);
            return ProofstackCli.INVALID;
        }
        Path profile = profileFile();
        if (profile == null) {
            err.println("Your name is needed to create the profile: use --name \"Your Name\".");
            return ProofstackCli.INVALID;
        }

        step("1/4 Profile");
        if (Files.isRegularFile(profile)) {
            out.println("  Using " + profile);
        } else if (run("init", profile.getParent().toString(), "--name", name.strip(), "--headline",
                headline == null || headline.isBlank() ? ask("Your role", "Backend Developer") : headline.strip(),
                "--language", language) != ProofstackCli.OK) {
            return ProofstackCli.INVALID;
        }

        step("2/4 GitHub");
        String user = gitHubUser(profile);
        if (user == null) {
            out.println("  Skipped: no GitHub user.");
        } else {
            linkGitHub(profile, user);
            replaceWith(profile, true, "import", "github", user, "--profile", profile.toString());
        }

        step("3/4 LinkedIn");
        if (skipLinkedIn) {
            out.println("  Skipped.");
        } else if (linkedInZip != null) {
            replaceWith(profile, false, "import", "linkedin", linkedInZip.toString(), "--merge-into", profile.toString());
        } else if (configureLinkedIn()) {
            out.println("  Opening LinkedIn in your browser: click Allow.");
            out.flush();
            replaceWith(profile, false, "import", "linkedin-api", "--merge-into", profile.toString());
        } else {
            out.println("  Skipped. Later: Studio > Traer de LinkedIn, or proofstack setup again.");
        }

        step("4/4 What is left");
        run("doctor", profile.toString());
        if (!problems.isEmpty()) {
            out.println();
            out.println("Some steps did not finish: " + String.join("; ", problems) + ". The rest is done.");
        }
        out.println();
        out.println("Next: complete each project (problem, solution, result) in Studio, save, then publish with git push.");
        out.flush();
        if (noStudio) {
            out.println("  proofstack studio " + profile);
            return problems.isEmpty() ? ProofstackCli.OK : ProofstackCli.ERROR;
        }
        return run("studio", profile.toString());
    }

    // ------------------------------------------------------------------ steps

    private Path profileFile() {
        if (dir != null) {
            Path file = dir.resolve("profile.yaml");
            if (!Files.isRegularFile(file) && (name == null || name.isBlank())) {
                name = ask("Your name", null);
            }
            return Files.isRegularFile(file) || (name != null && !name.isBlank()) ? file : null;
        }
        if (name == null || name.isBlank()) {
            name = ask("Your name", null);
        }
        if (name == null || name.isBlank() || slug(name).isEmpty()) {
            return null;
        }
        return Path.of("profiles", slug(name), "profile.yaml");
    }

    /** The option, else the profile's GitHub link, else the answer. */
    private String gitHubUser(Path profile) {
        if (github != null && !github.isBlank()) {
            return github.strip();
        }
        String linked = new YamlProfileSource().load(profile).person().links().stream()
                .map(Link::url).map(SetupCommand::gitHubUserOf).filter(u -> u != null).findFirst().orElse(null);
        if (linked != null) {
            return linked;
        }
        String answer = ask("Your GitHub user (Enter to skip)", "");
        if (answer == null || answer.isBlank()) {
            return null;
        }
        if (!validGitHubUser(answer.strip())) {
            problems.add("'" + answer.strip() + "' is not a GitHub user name");
            return null;
        }
        return answer.strip();
    }

    /** Adds the GitHub link to the profile when it does not have one, so the site and the CV show it. */
    private void linkGitHub(Path profile, String user) {
        Profile p = new YamlProfileSource().load(profile);
        if (p.person().links().stream().anyMatch(l -> gitHubUserOf(l.url()) != null)) {
            return;
        }
        PersonalInfo person = p.person();
        List<Link> links = new ArrayList<>(person.links());
        links.add(new Link("GitHub", "https://github.com/" + user));
        Profile updated = new Profile(p.language(), new PersonalInfo(person.name(), person.headline(), person.summary(),
                person.location(), person.email(), links), p.experience(), p.education(), p.projects(), p.skills(),
                p.evidence(), p.labs(), p.languages(), p.certifications());
        try {
            String before = Files.readString(profile, StandardCharsets.UTF_8);
            new YamlProfileWriter().write(updated, profile, true);
            Files.writeString(profile, keepHeader(before, Files.readString(profile, StandardCharsets.UTF_8)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.println("  Linked https://github.com/" + user);
    }

    /** True when LinkedIn can be imported now: already set up, or set up here with the user's app. */
    private boolean configureLinkedIn() {
        if (LinkedInSettings.configured(parent.env())) {
            return !"n".equalsIgnoreCase(ask("Import your LinkedIn profile now? [Y/n]", "y").strip());
        }
        if (yes) {
            return false;
        }
        out.println("  LinkedIn only lets you read your own data with an app of your own (one time, 5 minutes):");
        out.println("    1. https://www.linkedin.com/developers/apps/new  (any name; a company page is required)");
        out.println("    2. Products: request \"Member Data Portability API (Member)\"");
        out.println("    3. Auth: add the redirect URL " + LinkedInSettings.DEFAULT_REDIRECT);
        out.println("  Step by step: docs/linkedin-api.md. Only for members in the EU, EEA and Switzerland.");
        String clientId = ask("Client ID (Enter to skip)", "");
        if (clientId == null || clientId.isBlank()) {
            return false;
        }
        String secret = secret("Client Secret");
        try {
            Path saved = LinkedInSettings.save(parent.env(), clientId, secret, null);
            out.println("  Saved in " + saved + " (only you can read it). The login token is never saved.");
            return true;
        } catch (IllegalArgumentException | UncheckedIOException e) {
            problems.add("LinkedIn app not saved: " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Runs an import into a temporary file and, when it worked, puts it in place of the profile. The import commands
     * never touch their input; the profile is only replaced by a complete, valid file.
     */
    private void replaceWith(Path profile, boolean chooseRepos, String... command) {
        Path tmp = profile.resolveSibling("profile.setup.yaml");
        String[] args = new String[command.length + 3];
        System.arraycopy(command, 0, args, 0, command.length);
        args[command.length] = "--out";
        args[command.length + 1] = tmp.toString();
        args[command.length + 2] = "--force";
        try {
            if (run(args) == ProofstackCli.OK && Files.isRegularFile(tmp)) {
                YamlProfileSource source = new YamlProfileSource();
                if (chooseRepos) {
                    chooseRepositories(source.load(profile), tmp);
                }
                if (source.load(tmp).equals(source.load(profile))) {
                    // Nothing new: keep the file byte for byte, with its comments and its formatting.
                    return;
                }
                Files.writeString(tmp, keepHeader(Files.readString(profile, StandardCharsets.UTF_8),
                        Files.readString(tmp, StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
                Files.move(tmp, profile, StandardCopyOption.REPLACE_EXISTING);
            } else {
                problems.add(command[0] + " " + command[1]);
            }
        } catch (IOException e) {
            problems.add("cannot replace " + profile + ": " + e.getMessage());
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // a leftover temporary file is harmless and overwritten next time
            }
        }
    }

    /** Each step on a fresh command line: picocli command objects are not meant to be re-entered. */
    /**
     * Lets the user pick which new repositories become projects; the others go to github-ignore.txt and are not
     * offered again. Without questions (--yes, a workflow) all of them stay, for a pull request to review.
     */
    private void chooseRepositories(Profile before, Path tmp) {
        Profile after = new YamlProfileSource().load(tmp);
        java.util.Set<String> known = before.projects().stream().map(dev.proofstack.core.domain.Project::id)
                .collect(java.util.stream.Collectors.toSet());
        List<dev.proofstack.core.domain.Project> fresh = after.projects().stream()
                .filter(p -> !known.contains(p.id())).toList();
        if (yes || fresh.isEmpty()) {
            return;
        }
        out.println("  New repositories. Which ones go into your portfolio?");
        for (int i = 0; i < fresh.size(); i++) {
            out.println(String.format(Locale.ROOT, "   %3d  %s", i + 1, fresh.get(i).name()));
        }
        String answer = ask("Numbers separated by commas, \"all\", or Enter for none", "");
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        if ("all".equalsIgnoreCase(answer.strip())) {
            for (int i = 0; i < fresh.size(); i++) {
                keep.add(i);
            }
        } else {
            for (String part : answer.split("[,\\s]+")) {
                try {
                    int n = Integer.parseInt(part.strip());
                    if (n >= 1 && n <= fresh.size()) {
                        keep.add(n - 1);
                    }
                } catch (NumberFormatException ignored) {
                    // not a number: skipped
                }
            }
        }
        List<String> dropped = new ArrayList<>();
        java.util.Set<String> droppedIds = new java.util.HashSet<>();
        for (int i = 0; i < fresh.size(); i++) {
            if (!keep.contains(i)) {
                dropped.add(fresh.get(i).name());
                droppedIds.add(fresh.get(i).id());
            }
        }
        if (dropped.isEmpty()) {
            return;
        }
        Profile chosen = new Profile(after.language(), after.person(), after.experience(), after.education(),
                after.projects().stream().filter(p -> !droppedIds.contains(p.id())).toList(), after.skills(),
                after.evidence(), after.labs(), after.languages(), after.certifications());
        new YamlProfileWriter().write(chosen, tmp, true);
        Path ignore = GitHubIgnore.fileFor(tmp);
        GitHubIgnore.add(ignore, dropped);
        out.println("  " + (fresh.size() - dropped.size()) + " added. The other " + dropped.size()
                + " will not be offered again (" + ignore.getFileName() + ").");
    }

    /** The comment lines at the top of {@code original} in place of the ones the writer put at the top of {@code updated}. */
    static String keepHeader(String original, String updated) {
        return leadingComments(original) + updated.substring(leadingComments(updated).length());
    }

    private static String leadingComments(String text) {
        int end = 0;
        while (end < text.length()) {
            int eol = text.indexOf('\n', end);
            String line = (eol < 0 ? text.substring(end) : text.substring(end, eol)).strip();
            if (!line.startsWith("#") && !line.isEmpty()) {
                break;
            }
            end = eol < 0 ? text.length() : eol + 1;
        }
        return text.substring(0, end);
    }

    private int run(String... args) {
        PrintWriter err = spec.commandLine().getErr();
        int code = ProofstackCli.run(args, out, err, parent.env());
        out.flush();
        err.flush();
        return code;
    }

    private void step(String title) {
        out.println();
        out.println("== " + title);
        out.flush();
    }

    private String ask(String question, String fallback) {
        if (yes) {
            return fallback;
        }
        out.print("  " + question + (fallback == null || fallback.isEmpty() ? "" : " [" + fallback + "]") + ": ");
        out.flush();
        try {
            String line = input.readLine();
            return line == null || line.isBlank() ? fallback : line.strip();
        } catch (IOException e) {
            return fallback;
        }
    }

    /** Not echoed when there is a terminal. */
    private String secret(String question) {
        Console console = System.console();
        if (console != null) {
            char[] typed = console.readPassword("  %s: ", question);
            return typed == null ? "" : new String(typed);
        }
        return ask(question, "");
    }

    static String slug(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    static boolean validGitHubUser(String user) {
        return user.matches("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?");
    }

    static String gitHubUserOf(String url) {
        var m = java.util.regex.Pattern.compile("^https?://(?:www\\.)?github\\.com/([A-Za-z0-9-]{1,39})/?$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(url == null ? "" : url.strip());
        return m.matches() ? m.group(1) : null;
    }
}
