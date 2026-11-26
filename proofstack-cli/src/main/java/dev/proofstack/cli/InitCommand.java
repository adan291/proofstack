package dev.proofstack.cli;

import dev.proofstack.adapter.yaml.YamlProfileSource;
import dev.proofstack.adapter.yaml.YamlProfileWriter;
import dev.proofstack.core.application.ScaffoldService;
import dev.proofstack.core.domain.InvalidValueException;
import dev.proofstack.core.port.out.ProfileWriter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/** {@code proofstack init}: the first command a new user runs after "Use this template". */
@Command(name = "init", description = {"Create a starter profile.yaml to fill in.",
        "The file is valid but full of TODOs: 'proofstack doctor' tells you what is left before publishing."})
final class InitCommand implements Callable<Integer> {

    @Parameters(index = "0", paramLabel = "DIR", description = "Directory for profile.yaml, e.g. profiles/ada")
    Path dir;

    @Option(names = {"-n", "--name"}, required = true, description = "Your name")
    String name;

    @Option(names = {"-t", "--headline"}, required = true, description = "Your role, e.g. \"Backend Engineer · Java\"")
    String headline;

    @Option(names = {"-l", "--language"}, defaultValue = "en", description = "en or es (default: ${DEFAULT-VALUE})")
    String language;

    @Option(names = "--force", description = "Overwrite an existing profile.yaml")
    boolean force;

    @Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        PrintWriter err = spec.commandLine().getErr();
        if (!language.equals("en") && !language.equals("es")) {
            err.println("Unsupported language '" + language + "'. Use en or es.");
            return ProofstackCli.INVALID;
        }
        Path target = dir.resolve("profile.yaml");
        try {
            new ScaffoldService(new YamlProfileWriter()).scaffold(target, name, headline, language, force);
            new YamlProfileSource().load(target); // the starter must always be valid
            out.println("OK Created " + target.toAbsolutePath().normalize());
            out.println("  Next: edit it, then run  proofstack doctor " + target);
            return ProofstackCli.OK;
        } catch (ProfileWriter.TargetExistsException | InvalidValueException e) {
            err.println(e.getMessage());
            return ProofstackCli.INVALID;
        } catch (UncheckedIOException e) {
            err.println(e.getMessage());
            return ProofstackCli.ERROR;
        }
    }
}
