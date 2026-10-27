package dev.proofstack.cli;

import dev.proofstack.adapter.in.yaml.YamlProfileSource;
import dev.proofstack.core.domain.Profile;
import dev.proofstack.core.domain.ProfileValidationException;
import dev.proofstack.core.domain.readiness.PublishReadiness;
import dev.proofstack.core.domain.readiness.PublishReadiness.Finding;
import dev.proofstack.core.domain.readiness.PublishReadiness.Severity;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/** {@code proofstack doctor}: "can I publish this?" Used locally and as a gate in the Pages workflow. */
@Command(name = "doctor", description = {"Check that a valid profile is also ready to be public.",
        "Errors (placeholders, sample links) block publishing; warnings are advice. Exit 1 on errors,",
        "or on warnings too with --strict."})
final class DoctorCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "PROFILE", description = "Path to profile.yaml")
    Path profile;

    @Option(names = "--strict", description = "Fail on warnings as well")
    boolean strict;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!Files.isRegularFile(profile)) {
            err.println("File not found: " + profile);
            return ProofstackCli.ERROR;
        }
        Profile loaded;
        try {
            loaded = new YamlProfileSource().load(profile);
        } catch (ProfileValidationException e) {
            err.println("The profile is not valid yet; fix this first (proofstack validate):");
            e.violations().forEach(v -> err.println("  - " + v));
            return ProofstackCli.INVALID;
        } catch (UncheckedIOException e) {
            err.println(e.getMessage());
            return ProofstackCli.ERROR;
        }

        List<Finding> findings = PublishReadiness.check(loaded);
        for (Finding f : findings) {
            String level = f.severity() == Severity.ERROR ? "ERROR" : "WARN ";
            out.println(level + " " + f.path() + ": " + f.message());
            if (!f.hint().isEmpty()) {
                out.println("       → " + f.hint());
            }
        }
        long errors = findings.stream().filter(f -> f.severity() == Severity.ERROR).count();
        long warnings = findings.size() - errors;
        if (findings.isEmpty()) {
            out.println("OK " + profile + " is ready to publish");
        } else {
            out.println();
            out.println(errors + " error(s), " + warnings + " warning(s). "
                    + (errors > 0 ? "Not ready to publish." : "Publishable; warnings are suggestions."));
        }
        return errors > 0 || (strict && warnings > 0) ? ProofstackCli.INVALID : ProofstackCli.OK;
    }
}
