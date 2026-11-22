package software.spool.cli;

import software.spool.cli.program.InstalledPrograms;
import software.spool.cli.program.PathPrograms;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * What a command may touch outside the JVM: the folder it runs in, the environment, the terminal and the programs
 * installed. Commands receive it instead of reaching for {@code System}, so they run the same in a test.
 */
public record CommandContext(Path workingFolder, UnaryOperator<String> environment, Terminal terminal,
                             InstalledPrograms programs) {

    public static CommandContext ofTheMachine() {
        Terminal terminal = Terminal.ofTheConsole();
        return new CommandContext(Path.of("").toAbsolutePath(), System::getenv, terminal,
                new PathPrograms(System::getenv, terminal));
    }

    public Path resolve(Path path) {
        return workingFolder.resolve(path).normalize();
    }
}
