package dev.proofstack.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import dev.proofstack.adapter.in.linkedin.LinkedInExportImporter;
import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.adapter.out.llm.LlmProviders;
import dev.proofstack.adapter.out.web.ArchitectureThemeRenderer;
import dev.proofstack.adapter.yaml.YamlProfileWriter;
import dev.proofstack.core.application.ContentService;
import dev.proofstack.core.application.PortfolioService;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.Violation;
import dev.proofstack.core.domain.content.ContentFormat;
import dev.proofstack.core.domain.content.ContentGenerationException;
import dev.proofstack.core.domain.content.GeneratedContent;
import dev.proofstack.core.port.in.ImportProfileUseCase.ImportReport;
import dev.proofstack.core.port.in.ValidationResult;
import dev.proofstack.core.port.out.Artifact;
import dev.proofstack.core.port.out.ProfileWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Composition root: wires adapters into the core and exposes the use cases as commands.
 * Exit codes: 0 ok, 1 invalid input (profile, subject, format), 2 environment error (I/O, provider).
 */
@Command(name = "proofstack",
        mixinStandardHelpOptions = true,
        scope = CommandLine.ScopeType.INHERIT, // every subcommand gets --help and --version

        versionProvider = ProofstackCli.Version.class,
        description = "Portfolio-as-code for backend engineers.",
        subcommands = {SetupCommand.class, InitCommand.class, ProofstackCli.Validate.class, DoctorCommand.class, ProofstackCli.Build.class, CvCommand.class, StudioCommand.class, ProofstackCli.Generate.class,
                ProofstackCli.Import.class})
public final class ProofstackCli implements Runnable {

    static final int OK = 0;
    static final int INVALID = 1;
    static final int ERROR = 2;

    @Spec
    CommandLine.Model.CommandSpec spec;

    private final Map<String, String> env;

    Map<String, String> env() {
        return env;
    }

    ProofstackCli(Map<String, String> env) {
        this.env = Map.copyOf(env);
    }

    public static void main(String[] args) {
        PrintWriter err = writer(System.err);
        warnIfStale(err);
        System.exit(run(args, writer(System.out), err, System.getenv()));
    }

    /** The launcher prefers the jar in target/: after a git pull that jar may be older than the code. */
    private static void warnIfStale(PrintWriter err) {
        try {
            Path jar = Path.of(ProofstackCli.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            newerSource(jar).ifPresent(source -> err.println("WARN proofstack.jar is older than " + source.getFileName()
                    + ": you are running the previous code. Rebuild with: mvn -DskipTests package -pl proofstack-cli -am"));
        } catch (RuntimeException | java.net.URISyntaxException e) {
            // Only a hint: never stop the command because of it.
        }
    }

    private static final java.util.Set<String> NOT_SOURCES = java.util.Set.of("target", ".git", "node_modules");

    /**
     * A source of the checkout newer than {@code jar}, when {@code jar} is the one built in
     * {@code proofstack-cli/target/}. A downloaded {@code proofstack.jar} is never compared: it has no sources.
     */
    static java.util.Optional<Path> newerSource(Path jar) {
        Path target = jar.toAbsolutePath().getParent();
        if (target == null || !target.getFileName().toString().equals("target") || target.getParent() == null
                || !target.getParent().getFileName().toString().equals("proofstack-cli")) {
            return java.util.Optional.empty();
        }
        Path repo = target.getParent().getParent();
        try {
            java.nio.file.attribute.FileTime built = Files.getLastModifiedTime(jar);
            try (var files = Files.walk(repo)) {
                return files
                        .filter(p -> repo.relativize(p).toString().replace('\\', '/').matches(".*src/main/.*|(.*/)?pom\\.xml"))
                        .filter(p -> java.util.stream.StreamSupport.stream(repo.relativize(p).spliterator(), false)
                                .noneMatch(part -> NOT_SOURCES.contains(part.toString())))
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            try {
                                return Files.getLastModifiedTime(p).compareTo(built) > 0;
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .findFirst();
            }
        } catch (IOException | UncheckedIOException e) {
            return java.util.Optional.empty();
        }
    }

    /**
     * UTF-8 when the output goes to a file or a pipe ({@code generate > draft.md}); the console's own charset when it
     * is a terminal, so accents stay readable in a Windows console instead of turning into mojibake.
     */
    private static PrintWriter writer(java.io.PrintStream stream) {
        java.io.Console console = System.console();
        java.nio.charset.Charset charset = console != null && console.isTerminal()
                ? stream.charset() : StandardCharsets.UTF_8;
        return new PrintWriter(new java.io.OutputStreamWriter(stream, charset), true);
    }

    /** Entry point used by tests: no System.exit, injectable streams and environment. */
    static int run(String[] args, PrintWriter out, PrintWriter err, Map<String, String> env) {
        CommandLine cli = new CommandLine(new ProofstackCli(env));
        cli.setOut(out);
        cli.setErr(err);
        cli.setExitCodeExceptionMapper(e -> ERROR);
        return cli.execute(args);
    }

    static int run(String[] args, PrintWriter out, PrintWriter err) {
        return run(args, out, err, Map.of());
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    /** {@code --version}: read from a resource Maven fills in, so the pom is the only place to change it. */
    static final class Version implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws IOException {
            var props = new java.util.Properties();
            try (var in = ProofstackCli.class.getResourceAsStream("/proofstack/cli-version.properties")) {
                if (in == null) {
                    throw new IOException("missing /proofstack/cli-version.properties");
                }
                props.load(in);
            }
            return new String[]{"proofstack " + props.getProperty("version")};
        }
    }

    static PortfolioService portfolio(java.net.URI assistantUrl) {
        return portfolio(assistantUrl, java.util.Map.of());
    }

    static PortfolioService portfolio(java.net.URI assistantUrl, java.util.Map<String, String> alternates) {
        return new PortfolioService(new YamlProfileSource(), new ArchitectureThemeRenderer(assistantUrl, null, alternates),
                new FileSystemArtifactSink());
    }

    static PortfolioService portfolioWithCv(java.net.URI assistantUrl, String cvLink) {
        return portfolioWithCv(assistantUrl, cvLink, java.util.Map.of());
    }

    static PortfolioService portfolioWithCv(java.net.URI assistantUrl, String cvLink,
                                            java.util.Map<String, String> alternates) {
        var renderer = new SiteWithCvRenderer(new ArchitectureThemeRenderer(assistantUrl, cvLink, alternates),
                new dev.proofstack.adapter.out.cv.CvRenderer());
        return new PortfolioService(new YamlProfileSource(), renderer, new FileSystemArtifactSink());
    }

    private static Integer missing(Path file, PrintWriter err) {
        err.println("File not found: " + file);
        return ERROR;
    }

    /** Jobs of the user's profile that the import does not list: not in the new file, so say which ones. */
    static void printLeftOut(ImportReport report, PrintWriter err) {
        if (report.leftOut().isEmpty()) {
            return;
        }
        err.println("WARN " + report.leftOut().size() + " job(s) of your profile are not in the import and were left out"
                + " of the new file (your profile still has them):");
        report.leftOut().forEach(job -> err.println("  - " + job));
    }

    private static void printViolations(List<Violation> violations, PrintWriter err) {
        err.println("Invalid input (" + violations.size() + " problem" + (violations.size() == 1 ? "" : "s") + "):");
        violations.forEach(v -> err.println("  - " + v));
    }

    // ------------------------------------------------------------------ validate

    @Command(name = "validate", description = "Validate a profile.yaml against the schema and domain rules.")
    static final class Validate implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "PROFILE", description = "Path to profile.yaml")
        Path profile;

        @Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            if (!Files.isRegularFile(profile)) {
                return missing(profile, err);
            }
            try {
                ValidationResult result = portfolio(null).validate(profile);
                if (result.valid()) {
                    out.println("OK " + profile + " is valid");
                    return OK;
                }
                printViolations(result.violations(), err);
                return INVALID;
            } catch (UncheckedIOException e) {
                err.println(e.getMessage());
                return ERROR;
            }
        }
    }

    // ------------------------------------------------------------------ build

    @Command(name = "build", description = "Generate the static portfolio site.")
    static final class Build implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "PROFILE", description = "Path to profile.yaml")
        Path profile;

        @Option(names = {"-o", "--out"}, paramLabel = "DIR", defaultValue = "dist",
                description = "Output directory (default: ${DEFAULT-VALUE})")
        Path out;

        @Option(names = "--assistant-url", paramLabel = "URL",
                description = "Base URL of the optional ask-my-portfolio service (https; http only for localhost)")
        java.net.URI assistantUrl;

        @Option(names = "--with-cv",
                description = "Also publish the CV in <out>/cv/ and add a 'Download CV' link to the site")
        boolean withCv;

        @Option(names = "--cv-link", paramLabel = "PATH", defaultValue = "cv/cv.html",
                description = "Relative target of the CV link (default: ${DEFAULT-VALUE}; e.g. cv/cv.pdf when a PDF is added later)")
        String cvLink;

        @Option(names = "--alternate", paramLabel = "LANG=PATH",
                description = "Same portfolio in another language, linked from the page (repeatable), e.g. --alternate en=en/ or --alternate es=../")
        java.util.Map<String, String> alternates = new java.util.LinkedHashMap<>();

        @Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter stdout = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            if (!Files.isRegularFile(profile)) {
                return missing(profile, err);
            }
            PortfolioService service;
            try {
                service = withCv ? portfolioWithCv(assistantUrl, cvLink, alternates) : portfolio(assistantUrl, alternates);
            } catch (IllegalArgumentException e) {
                err.println(e.getMessage());
                return INVALID;
            }
            try {
                List<Artifact> artifacts = service.build(profile, out);
                stdout.println("OK Built " + artifacts.size() + " files into " + out.toAbsolutePath().normalize());
                artifacts.forEach(a -> stdout.println("  " + a.relativePath()));
                // The site shows the profile as it is; doctor is the gate, but say so before anyone publishes it.
                int todos = dev.proofstack.core.domain.TodoCounter.count(new YamlProfileSource().load(profile));
                if (todos > 0) {
                    err.println("WARN " + todos + " TODO placeholder" + (todos == 1 ? "" : "s")
                            + " in the site. Run 'proofstack doctor " + profile + "' before publishing.");
                }
                return OK;
            } catch (ProfileValidationException e) {
                printViolations(e.violations(), err);
                return INVALID;
            } catch (UncheckedIOException e) {
                err.println(e.getMessage());
                return ERROR;
            }
        }
    }

    // ------------------------------------------------------------------ generate

    @Command(name = "generate",
            description = {"Draft content about a project or evidence with an LLM.",
                    "FORMAT: linkedin-post, readme, cv-bullets, video-script, evidence-draft, notebooklm-brief.",
                    "Gemini reads the key from the GEMINI_API_KEY environment variable; Ollama uses OLLAMA_HOST."})
    static final class Generate implements Callable<Integer> {

        @ParentCommand
        ProofstackCli parent;

        @Parameters(index = "0", paramLabel = "FORMAT", description = "Output format")
        String format;

        @Parameters(index = "1", paramLabel = "PROFILE", description = "Path to profile.yaml")
        Path profile;

        @Option(names = {"-s", "--subject"}, required = true, paramLabel = "ID",
                description = "Id of the project or evidence to write about")
        String subject;

        @Option(names = {"-p", "--provider"}, defaultValue = "ollama", paramLabel = "NAME",
                description = "ollama (local, default) or gemini")
        String provider;

        @Option(names = {"-m", "--model"}, paramLabel = "MODEL",
                description = "Model name (default: llama3.2 for ollama, gemini-3.8-flash for gemini)")
        String model;

        @Option(names = {"-l", "--language"}, paramLabel = "TAG", description = "Content language (default: profile language)")
        String language;

        @Option(names = {"-o", "--out"}, paramLabel = "FILE", description = "Write Markdown to FILE instead of stdout")
        Path out;

        @Option(names = "--force", description = "Overwrite --out if it exists")
        boolean force;

        @Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            PrintWriter stdout = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();

            ContentFormat contentFormat = ContentFormat.fromSlug(format).orElse(null);
            if (contentFormat == null) {
                err.println("Unknown format '" + format + "'. Use one of: " + ContentFormat.slugs());
                return INVALID;
            }
            if (!Files.isRegularFile(profile)) {
                return missing(profile, err);
            }
            if (out != null && Files.exists(out) && !force) {
                err.println(out + " already exists (use --force to overwrite)");
                return INVALID;
            }

            try {
                LlmProviders.Provider llm = LlmProviders.Provider.parse(provider);
                var service = new ContentService(new YamlProfileSource(), LlmProviders.create(llm, model, parent.env));
                GeneratedContent content = service.generate(profile, subject, contentFormat, language);

                if (out == null) {
                    stdout.print(content.toMarkdown());
                    stdout.flush();
                } else {
                    Files.writeString(out, content.toMarkdown(), StandardCharsets.UTF_8);
                    stdout.println("OK Draft written to " + out.toAbsolutePath().normalize());
                }
                content.warnings().forEach(w -> err.println("WARNING " + w));
                return OK;
            } catch (IllegalArgumentException e) {
                err.println(e.getMessage());
                return INVALID;
            } catch (ProfileValidationException e) {
                printViolations(e.violations(), err);
                return INVALID;
            } catch (ContentGenerationException e) {
                err.println("Generation failed: " + e.getMessage());
                return e.kind() == ContentGenerationException.Kind.UNKNOWN_SUBJECT ? INVALID : ERROR;
            } catch (IOException | UncheckedIOException e) {
                err.println(e.getMessage());
                return ERROR;
            }
        }
    }

    // ------------------------------------------------------------------ import

    @Command(name = "import", description = "Bootstrap a profile.yaml from an external source.",
            subcommands = {Import.LinkedIn.class, LinkedInApiCommand.class, Import.GitHub.class})
    static final class Import implements Runnable {

        @ParentCommand
        ProofstackCli parent;

        @Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public void run() {
            spec.commandLine().usage(spec.commandLine().getOut());
        }

        @Command(name = "github",
                description = {"Add your public GitHub repositories as draft projects.",
                        "Writes a NEW file; the original profile is never modified. Forks and archived repos are skipped.",
                        "Optional: GITHUB_TOKEN raises the API rate limit (never pass it as an argument)."})
        static final class GitHub implements Callable<Integer> {

            @ParentCommand
            Import importCommand;

            @Parameters(index = "0", paramLabel = "USER", description = "GitHub username")
            String user;

            @Option(names = {"-p", "--profile"}, required = true, paramLabel = "FILE", description = "Existing profile.yaml")
            Path profile;

            @Option(names = {"-o", "--out"}, required = true, paramLabel = "FILE", description = "profile.yaml to create")
            Path out;

            @Option(names = "--include-forks", description = "Also import forks")
            boolean includeForks;

            @Option(names = "--force", description = "Overwrite --out if it exists")
            boolean force;

            @Spec
            CommandLine.Model.CommandSpec spec;

            @Override
            public Integer call() {
                PrintWriter stdout = spec.commandLine().getOut();
                PrintWriter err = spec.commandLine().getErr();
                if (!Files.isRegularFile(profile)) {
                    return missing(profile, err);
                }
                try {
                    var service = new dev.proofstack.core.application.ProjectMergeService(new YamlProfileSource(),
                            GitHubIgnore.filtered(dev.proofstack.adapter.in.github.GitHubProjectSource.fromEnvironment(
                                    importCommand.parent.env), GitHubIgnore.fileFor(profile)),
                            new YamlProfileWriter());
                    var report = service.merge(profile, user, includeForks, out, force);
                    stdout.println("OK Added " + report.added().size() + " project(s) from github.com/" + user
                            + " into " + out.toAbsolutePath().normalize());
                    report.added().forEach(id -> stdout.println("  + " + id));
                    if (!report.skipped().isEmpty()) {
                        stdout.println("  Already in your profile: " + String.join(", ", report.skipped()));
                    }
                    if (report.todoCount() > 0) {
                        stdout.println("  " + report.todoCount() + " TODO placeholders to review before publishing.");
                    }
                    return OK;
                } catch (dev.proofstack.core.port.out.ImportException e) {
                    err.println(e.getMessage());
                    return switch (e.kind()) {
                        case NOT_FOUND, INVALID_INPUT -> INVALID;
                        case RATE_LIMITED, UNAVAILABLE -> ERROR;
                    };
                } catch (IllegalArgumentException | ProfileWriter.TargetExistsException e) {
                    err.println(e.getMessage());
                    return INVALID;
                } catch (ProfileValidationException e) {
                    printViolations(e.violations(), err);
                    return INVALID;
                } catch (UncheckedIOException e) {
                    err.println(Objects.requireNonNullElse(e.getMessage(), "I/O error"));
                    return ERROR;
                }
            }
        }

        @Command(name = "linkedin",
                description = {"Import the official LinkedIn data export (Settings > Data privacy > Get a copy of your data).",
                        "Email and phone are never imported."})
        static final class LinkedIn implements Callable<Integer> {

            @Parameters(index = "0", paramLabel = "EXPORT_ZIP", description = "The ZIP downloaded from LinkedIn")
            Path export;

            @Option(names = {"-o", "--out"}, required = true, paramLabel = "FILE", description = "profile.yaml to create")
            Path out;

            @Option(names = "--force", description = "Overwrite FILE if it exists")
            boolean force;

            @Option(names = {"-m", "--merge-into"}, paramLabel = "PROFILE",
                    description = "Update this existing profile (kept unchanged) instead of starting from scratch")
            Path mergeInto;

            @Spec
            CommandLine.Model.CommandSpec spec;

            @Override
            public Integer call() {
                PrintWriter stdout = spec.commandLine().getOut();
                PrintWriter err = spec.commandLine().getErr();
                if (!Files.isRegularFile(export)) {
                    return missing(export, err);
                }
                if (mergeInto != null && !Files.isRegularFile(mergeInto)) {
                    return missing(mergeInto, err);
                }
                try {
                    LinkedInExportImporter zip = new LinkedInExportImporter();
                    ImportReport report = new dev.proofstack.core.application.CareerImportService(
                            new YamlProfileSource(), new YamlProfileWriter())
                            .importCareer(() -> zip.importProfile(export), mergeInto, out, force);
                    stdout.println("OK Imported " + report.counts().entrySet().stream()
                            .filter(e -> e.getValue() > 0)
                            .map(e -> e.getValue() + " " + e.getKey())
                            .collect(Collectors.joining(", ")) + " into " + out.toAbsolutePath().normalize());
                    if (report.todoCount() > 0) {
                        stdout.println("  " + report.todoCount() + " TODO placeholders to review before publishing.");
                    }
                    printLeftOut(report, err);
                    stdout.println("  Next: proofstack validate " + out);
                    return OK;
                } catch (ProfileWriter.TargetExistsException | IllegalArgumentException e) {
                    err.println(e.getMessage());
                    return INVALID;
                } catch (ProfileValidationException e) {
                    printViolations(e.violations(), err);
                    return INVALID;
                } catch (UncheckedIOException e) {
                    err.println(Objects.requireNonNullElse(e.getMessage(), "I/O error"));
                    return ERROR;
                }
            }
        }
    }
}
