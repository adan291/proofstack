package dev.proofstack.cli;

import java.util.Map;
import dev.proofstack.adapter.in.github.GitHubProjectSource;
import dev.proofstack.adapter.in.linkedin.LinkedInMemberDataSource;
import dev.proofstack.adapter.in.linkedin.LinkedInSettings;
import dev.proofstack.adapter.in.studio.StudioImports;
import dev.proofstack.adapter.yaml.YamlProfileJsonCodec;
import dev.proofstack.core.application.DraftImportService;
import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import dev.proofstack.adapter.in.studio.StudioServer;
import dev.proofstack.adapter.yaml.YamlProfileDocumentStore;
import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.adapter.out.cv.CvRenderer;
import dev.proofstack.adapter.out.web.ArchitectureThemeRenderer;
import dev.proofstack.core.application.ProfileEditingService;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** {@code proofstack studio}: edit a profile.yaml in the browser, with validation and a live preview. */
@Command(name = "studio", description = {"Edit a profile.yaml in your browser: forms, doctor findings and a preview.",
        "Listens on 127.0.0.1 only. Invalid profiles are never saved; the file as it was before the first save is kept as profile.yaml.bak.",
        "Stop it with Ctrl+C."})
final class StudioCommand implements Callable<Integer> {

    static final String SCHEMA = "/proofstack/profile.schema.json";

    @Parameters(index = "0", paramLabel = "PROFILE", description = "Path to profile.yaml")
    Path profile;

    @Option(names = "--port", paramLabel = "N", defaultValue = "0",
            description = "Port on 127.0.0.1 (default: any free port)")
    int port;

    @Option(names = "--no-browser", description = "Do not open the browser; just print the URL")
    boolean noBrowser;

    @Option(names = "--wizard", description = "Open the step-by-step assistant instead of the editor")
    boolean wizard;

    @picocli.CommandLine.ParentCommand
    ProofstackCli parent;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws InterruptedException {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!Files.isRegularFile(profile)) {
            err.println("File not found: " + profile);
            return ProofstackCli.ERROR;
        }
        if (port < 0 || port > 65535) {
            err.println("--port must be between 0 and 65535");
            return ProofstackCli.INVALID;
        }
        StudioServer server;
        try {
            server = start(profile, port, parent == null ? Map.of() : parent.env(), err);
        } catch (UncheckedIOException e) {
            err.println(e.getMessage());
            return ProofstackCli.ERROR;
        }
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            stopped.countDown();
        }));
        out.println("OK Studio is editing " + profile.toAbsolutePath().normalize());
        out.println("  Open: " + (wizard ? server.wizardUrl() : server.url()));
        out.println("  The link carries a token that works until you stop Studio: do not share it. Ctrl+C to stop.");
        out.flush();
        if (!noBrowser) {
            openBrowser(wizard ? server.wizardUrl() : server.url(), err);
        }
        stopped.await();
        return ProofstackCli.OK;
    }

    /** Wires Studio for one profile file. Package-private so tests can start it without blocking. */
    static StudioServer start(Path profile, int port) {
        return start(profile, port, Map.of(), new PrintWriter(java.io.Writer.nullWriter()));
    }

    /** @param env where the LinkedIn app and GITHUB_TOKEN come from; {@code log} gets the LinkedIn consent URL */
    static StudioServer start(Path profile, int port, Map<String, String> env, PrintWriter log) {
        Path file = profile.toAbsolutePath().normalize();
        YamlProfileSource source = new YamlProfileSource();
        StudioServer server = new StudioServer(new ProfileEditingService(new YamlProfileDocumentStore(file)),
                () -> source.load(file), new ArchitectureThemeRenderer(), new CvRenderer(), schema(), port,
                imports(env, log, file));
        try {
            server.start();
            return server;
        } catch (RuntimeException e) {
            server.close();
            throw e;
        }
    }

    /** LinkedIn and GitHub for the import buttons. The LinkedIn app comes from proofstack setup or the environment. */
    static StudioImports imports(Map<String, String> env, PrintWriter log, Path profile) {
        return new StudioImports(new DraftImportService(new YamlProfileJsonCodec()), () -> {
            if (!LinkedInSettings.configured(env)) {
                throw new IllegalStateException("LinkedIn is not configured yet: run ./proofstack setup once");
            }
            return LinkedInMemberDataSource.fromEnvironment(LinkedInSettings.withSaved(env), uri -> {
                log.println("  LinkedIn: authorise in your browser. If it did not open: " + uri);
                log.flush();
                browse(uri);
            });
        }, GitHubIgnore.filtered(GitHubProjectSource.fromEnvironment(env), GitHubIgnore.fileFor(profile)),
                () -> LinkedInSettings.configured(env),
                (id, secret) -> LinkedInSettings.save(env, id, secret, null),
                names -> GitHubIgnore.add(GitHubIgnore.fileFor(profile), names),
                () -> workspace(profile),
                GitHubProjectSource.fromEnvironment(env),
                () -> GitHubIgnore.read(GitHubIgnore.fileFor(profile)),
                names -> GitHubIgnore.remove(GitHubIgnore.fileFor(profile), names));
    }

    /** The GitHub repository the profile lives in (from .git/config), for the links of the publishing step. */
    static StudioImports.Workspace workspace(Path profile) {
        Path file = profile.toAbsolutePath().normalize();
        for (Path dir = file.getParent(); dir != null; dir = dir.getParent()) {
            Path config = dir.resolve(".git").resolve("config");
            if (!Files.isRegularFile(config)) {
                continue;
            }
            String relative = dir.relativize(file).toString().replace('\\', '/');
            try {
                return fromGitConfig(Files.readString(config, StandardCharsets.UTF_8), relative);
            } catch (IOException e) {
                return new StudioImports.Workspace(null, null, relative);
            }
        }
        return StudioImports.Workspace.UNKNOWN;
    }

    static StudioImports.Workspace fromGitConfig(String config, String profilePath) {
        boolean origin = false;
        for (String raw : config.split("\\R")) {
            String line = raw.strip();
            if (line.startsWith("[")) {
                origin = line.equals("[remote \"origin\"]");
            } else if (origin && line.startsWith("url")) {
                var m = java.util.regex.Pattern.compile("github\\.com[:/]([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100}?)(?:\\.git)?/?$")
                        .matcher(line);
                if (m.find()) {
                    return new StudioImports.Workspace(m.group(1), m.group(2), profilePath);
                }
            }
        }
        return new StudioImports.Workspace(null, null, profilePath);
    }

    private static void browse(java.net.URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // the URL is in the terminal
        }
    }

    private static String schema() {
        try (InputStream in = YamlProfileSource.class.getResourceAsStream(SCHEMA)) {
            if (in == null) {
                throw new IllegalStateException("Missing schema resource " + SCHEMA);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void openBrowser(java.net.URI url, PrintWriter err) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(url);
                return;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // fall through to the message below
        }
        err.println("  Could not open a browser: open the URL above by hand.");
    }
}
