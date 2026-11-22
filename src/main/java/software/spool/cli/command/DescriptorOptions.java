package software.spool.cli.command;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.spool.cli.CommandContext;
import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.descriptors.SpoolNodeDescriptor;
import software.spool.dsl.source.FileSystemDescriptorSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The descriptor a command works on, its profile and the classes of the project, as a node would load them. */
public final class DescriptorOptions {
    @Parameters(index = "0", paramLabel = "DESCRIPTOR", description = "The descriptor, such as Spool.yaml.")
    private Path descriptor;

    @Option(names = "--profile", description = "The profile to apply. By default, the one in SPOOL_PROFILE.")
    private Optional<String> profile = Optional.empty();

    @Option(names = "--classpath", split = "${sys:path.separator}", paramLabel = "PATH",
            description = "Where the classes of the project are. By default, those of the Maven project in the folder.")
    private List<Path> classpath = List.of();

    public String name() {
        return descriptor.toString();
    }

    public Path file(CommandContext context) {
        return context.resolve(descriptor);
    }

    public Optional<String> profile(CommandContext context) {
        return profile.or(() -> loader(context).profileOfTheEnvironment());
    }

    public List<Path> classpath() {
        return classpath;
    }

    /** Loads the descriptor and hands it to {@code use}, so its errors name the file and the key, as in a node. */
    public <T> T load(CommandContext context, Function<SpoolNodeDescriptor, T> use) throws IOException {
        return loader(context).load(FileSystemDescriptorSource.locationOf(file(context)), profile(context), use);
    }

    private static DescriptorLoader loader(CommandContext context) {
        return DescriptorLoader.fromFileSystem(context.environment());
    }
}
