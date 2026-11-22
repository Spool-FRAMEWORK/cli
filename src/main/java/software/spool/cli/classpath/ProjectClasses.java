package software.spool.cli.classpath;

import software.spool.cli.CommandContext;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;

/**
 * Runs work with the classes of the user's project visible as the context class loader, which is where Spool looks
 * for aggregators, event classes and plugins. The classpath given wins; otherwise the first source that knows the
 * folder gives it; otherwise only the classes of the CLI are seen.
 */
public final class ProjectClasses {
    private final CommandContext context;

    public ProjectClasses(CommandContext context) {
        this.context = context;
    }

    public <T> T within(List<Path> given, Callable<T> work) throws Exception {
        List<Path> classpath = given.isEmpty() ? found().orElse(List.of()) : given.stream().map(context::resolve).toList();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try (URLClassLoader project = new URLClassLoader(urls(classpath), previous)) {
            thread.setContextClassLoader(project);
            return work.call();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    private Optional<List<Path>> found() {
        return ServiceLoader.load(ClasspathSource.class).stream()
                .map(source -> source.get().find(context))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static URL[] urls(List<Path> classpath) {
        return classpath.stream().map(ProjectClasses::url).toArray(URL[]::new);
    }

    private static URL url(Path entry) {
        try {
            return entry.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new UncheckedIOException(new IOException("Not a valid classpath entry: " + entry, exception));
        }
    }
}
