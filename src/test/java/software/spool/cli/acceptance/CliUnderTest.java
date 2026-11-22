package software.spool.cli.acceptance;

import software.spool.cli.CommandContext;
import software.spool.cli.SpoolCli;
import software.spool.cli.Terminal;
import software.spool.cli.program.ExternalProgram;
import software.spool.cli.program.InstalledPrograms;
import software.spool.cli.program.PathPrograms;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The CLI run in the test's own JVM, in a folder of its own, with a fake Terraform the scenario controls. */
public class CliUnderTest {
    private final Path folder;
    private final Map<String, String> environment = new HashMap<>();
    private final List<Run> terraformRuns = new ArrayList<>();
    private boolean terraformInstalled = true;
    private String output = "";
    private String error = "";
    private int exitCode = -1;

    public CliUnderTest() throws IOException {
        folder = Files.createTempDirectory("spool-cli-test");
        environment.put("PATH", System.getenv("PATH"));
        Optional.ofNullable(System.getenv("PATHEXT")).ifPresent(value -> environment.put("PATHEXT", value));
    }

    public Path folder() {
        return folder;
    }

    public void setEnvironment(String name, String value) {
        environment.put(name, value);
    }

    public void terraformInstalled(boolean installed) {
        terraformInstalled = installed;
    }

    public void run(String commandLine, String answers) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(new BufferedReader(new StringReader(answers)), new PrintWriter(out, true),
                new PrintWriter(err, true));
        exitCode = SpoolCli.run(arguments(commandLine), new CommandContext(folder, environment::get, terminal, programs(terminal)));
        output = out.toString();
        error = err.toString();
    }

    public int exitCode() {
        return exitCode;
    }

    public String output() {
        return output;
    }

    public String error() {
        return error;
    }

    public List<Run> terraformRuns() {
        return List.copyOf(terraformRuns);
    }

    private InstalledPrograms programs(Terminal terminal) {
        InstalledPrograms machine = new PathPrograms(environment::get, terminal);
        return name -> name.equals("terraform") ? fakeTerraform() : machine.find(name);
    }

    private Optional<ExternalProgram> fakeTerraform() {
        return terraformInstalled ? Optional.of((runFolder, arguments) -> {
            terraformRuns.add(new Run(runFolder, arguments));
            return 0;
        }) : Optional.empty();
    }

    private static String[] arguments(String commandLine) {
        List<String> words = Arrays.asList(commandLine.trim().split("\\s+"));
        return words.subList(1, words.size()).toArray(String[]::new);
    }

    public record Run(Path folder, List<String> arguments) {
    }
}
