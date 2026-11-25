package dev.proofstack.cli;

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
import dev.proofstack.adapter.in.yaml.YamlProfileDocumentStore;
import dev.proofstack.adapter.in.yaml.YamlProfileSource;
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
        "Listens on 127.0.0.1 only. Invalid profiles are never saved; the previous file is kept as profile.yaml.bak.",
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
            server = start(profile, port);
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
        out.println("  Open: " + server.url());
        out.println("  The link carries a token that works until you stop Studio: do not share it. Ctrl+C to stop.");
        out.flush();
        if (!noBrowser) {
            openBrowser(server, err);
        }
        stopped.await();
        return ProofstackCli.OK;
    }

    /** Wires Studio for one profile file. Package-private so tests can start it without blocking. */
    static StudioServer start(Path profile, int port) {
        Path file = profile.toAbsolutePath().normalize();
        YamlProfileSource source = new YamlProfileSource();
        StudioServer server = new StudioServer(new ProfileEditingService(new YamlProfileDocumentStore(file)),
                () -> source.load(file), new ArchitectureThemeRenderer(), new CvRenderer(), schema(), port);
        try {
            server.start();
            return server;
        } catch (RuntimeException e) {
            server.close();
            throw e;
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

    private static void openBrowser(StudioServer server, PrintWriter err) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(server.url());
                return;
            }
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // fall through to the message below
        }
        err.println("  Could not open a browser: open the URL above by hand.");
    }
}
