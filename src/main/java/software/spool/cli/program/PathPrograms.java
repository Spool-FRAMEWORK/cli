package software.spool.cli.program;

import software.spool.cli.Terminal;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Finds a program in the variable {@code <NAME>_BINARY}, such as {@code TERRAFORM_BINARY}, or else on the
 * {@code PATH}.
 */
public final class PathPrograms implements InstalledPrograms {
    private static final String BINARY_SUFFIX = "_BINARY";
    private final UnaryOperator<String> environment;
    private final Terminal terminal;

    public PathPrograms(UnaryOperator<String> environment, Terminal terminal) {
        this.environment = environment;
        this.terminal = terminal;
    }

    @Override
    public Optional<ExternalProgram> find(String name) {
        return binaryOf(name).map(binary -> new ProcessProgram(binary, terminal));
    }

    Optional<Path> binaryOf(String name) {
        return variable(name.toUpperCase(Locale.ROOT) + BINARY_SUFFIX).map(Path::of).filter(Files::isRegularFile)
                .or(() -> onThePath(name));
    }

    private Optional<Path> onThePath(String name) {
        return variable("PATH").stream()
                .flatMap(path -> Arrays.stream(path.split(File.pathSeparator)))
                .filter(folder -> !folder.isBlank())
                .flatMap(folder -> extensions().map(extension -> Path.of(folder, name + extension)))
                .filter(Files::isRegularFile)
                .findFirst();
    }

    /** On Windows only the extensions of {@code PATHEXT} run, so a bare {@code mvn}, a shell script, is skipped. */
    private Stream<String> extensions() {
        return variable("PATHEXT").map(value -> Arrays.stream(value.toLowerCase(Locale.ROOT).split(";")))
                .orElseGet(() -> Stream.of(""));
    }

    private Optional<String> variable(String name) {
        return Optional.ofNullable(environment.apply(name)).filter(value -> !value.isBlank());
    }
}
