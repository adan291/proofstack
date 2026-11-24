package dev.proofstack.cli;

import dev.proofstack.adapter.in.linkedin.LinkedInMemberDataSource;
import dev.proofstack.adapter.in.yaml.YamlProfileSource;
import dev.proofstack.adapter.out.yaml.YamlProfileWriter;
import dev.proofstack.core.application.CareerImportService;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.port.in.ImportProfileUseCase.ImportReport;
import dev.proofstack.core.port.out.ProfileWriter;
import dev.proofstack.core.port.out.ProjectSource.ImportException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import java.awt.Desktop;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** {@code proofstack import linkedin-api}: pull the member's own profile, no ZIP needed (docs/linkedin-api.md). */
@Command(name = "linkedin-api",
        description = {"Import your own LinkedIn profile through the Member Data Portability API (EU members).",
                "Needs LINKEDIN_CLIENT_ID, LINKEDIN_CLIENT_SECRET and LINKEDIN_REDIRECT_URI (or LINKEDIN_ACCESS_TOKEN)",
                "as environment variables. Opens your browser to authorise; the token is never saved."})
final class LinkedInApiCommand implements Callable<Integer> {

    @ParentCommand
    ProofstackCli.Import importCommand;

    @Option(names = {"-o", "--out"}, required = true, paramLabel = "FILE", description = "profile.yaml to create")
    Path out;

    @Option(names = {"-m", "--merge-into"}, paramLabel = "PROFILE",
            description = "Update this existing profile (kept unchanged): career from LinkedIn, proofs from you")
    Path mergeInto;

    @Option(names = "--force", description = "Overwrite FILE if it exists")
    boolean force;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter stdout = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (mergeInto != null && !Files.isRegularFile(mergeInto)) {
            err.println("File not found: " + mergeInto);
            return ProofstackCli.ERROR;
        }
        if (Files.exists(out) && !force) {
            err.println(out + " already exists (use --force to overwrite)");
            return ProofstackCli.INVALID;
        }
        try {
            LinkedInMemberDataSource linkedIn = LinkedInMemberDataSource.fromEnvironment(importCommand.parent.env(),
                    uri -> openBrowser(uri, stdout));
            ImportReport report = new CareerImportService(new YamlProfileSource(), new YamlProfileWriter())
                    .importCareer(linkedIn, mergeInto, out, force);
            stdout.println("OK Imported " + report.counts().entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(e -> e.getValue() + " " + e.getKey())
                    .collect(Collectors.joining(", ")) + " into " + out.toAbsolutePath().normalize());
            if (report.todoCount() > 0) {
                stdout.println("  " + report.todoCount() + " TODO placeholders left. Next: proofstack doctor " + out);
            }
            return ProofstackCli.OK;
        } catch (ImportException e) {
            err.println(e.getMessage());
            return switch (e.kind()) {
                case INVALID_INPUT, NOT_FOUND -> ProofstackCli.INVALID;
                case RATE_LIMITED, UNAVAILABLE -> ProofstackCli.ERROR;
            };
        } catch (ProfileWriter.TargetExistsException | IllegalArgumentException e) {
            err.println(e.getMessage());
            return ProofstackCli.INVALID;
        } catch (ProfileValidationException e) {
            err.println("Invalid profile:");
            e.violations().forEach(v -> err.println("  - " + v));
            return ProofstackCli.INVALID;
        } catch (UncheckedIOException e) {
            err.println(e.getMessage());
            return ProofstackCli.ERROR;
        }
    }

    private static void openBrowser(URI uri, PrintWriter stdout) {
        stdout.println("Authorise Proofstack in your browser. If it does not open, visit:");
        stdout.println("  " + uri);
        stdout.flush();
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (Exception ignored) {
            // The URL is printed above; opening the browser is only a convenience.
        }
    }
}
