package software.spool.cli.classpath;

import software.spool.cli.CommandContext;
import software.spool.cli.program.ExternalProgram;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The classes of a Maven project: its {@code target/classes} and the dependencies Maven resolves for it. */
public final class MavenClasspath implements ClasspathSource {
    private static final String POM = "pom.xml";
    private static final Path CLASSES = Path.of("target", "classes");

    @Override
    public Optional<List<Path>> find(CommandContext context) {
        if (!Files.isRegularFile(context.workingFolder().resolve(POM))) return Optional.empty();
        ExternalProgram maven = context.programs().find("mvn").orElseThrow(() -> new IllegalStateException(
                "The folder has a pom.xml but Maven is not installed: install it, set MVN_BINARY or pass --classpath"));
        List<Path> classpath = new ArrayList<>(List.of(context.workingFolder().resolve(CLASSES)));
        classpath.addAll(dependencies(maven, context.workingFolder()));
        return Optional.of(classpath);
    }

    private static List<Path> dependencies(ExternalProgram maven, Path project) {
        try {
            Path listing = Files.createTempFile("spool-classpath", ".txt");
            int exitCode = maven.run(project, List.of("-q", "dependency:build-classpath", "-Dmdep.outputFile=" + listing));
            if (exitCode != 0) throw new IllegalStateException("Maven could not resolve the dependencies of the project, exit code " + exitCode);
            String entries = Files.readString(listing).strip();
            Files.delete(listing);
            return entries.isEmpty() ? List.of() : Arrays.stream(entries.split(File.pathSeparator)).map(Path::of).toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read the classpath Maven wrote: " + exception.getMessage(), exception);
        }
    }
}
