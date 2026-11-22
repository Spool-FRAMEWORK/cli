package software.spool.cli.classpath;

import software.spool.cli.CommandContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * A way to find the classes of the project in the folder the CLI runs in, such as its Maven build. Found with
 * {@link java.util.ServiceLoader}: another build tool is another class listed in
 * {@code META-INF/services/software.spool.cli.classpath.ClasspathSource}.
 */
public interface ClasspathSource {
    /** The classpath of the project, or empty when the folder is not a project this source knows. */
    Optional<List<Path>> find(CommandContext context);
}
