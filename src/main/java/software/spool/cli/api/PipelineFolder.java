package software.spool.cli.api;

import software.spool.dsl.DescriptorFiles;
import software.spool.dsl.source.DescriptorSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A descriptor and the files it includes, read from the folder of the descriptor, each by its path relative to it with
 * forward slashes, as the Spool API takes a pipeline (ADR-036).
 */
public final class PipelineFolder implements DescriptorSource {
    private final Path folder;

    private PipelineFolder(Path folder) {
        this.folder = folder;
    }

    /**
     * The files of the descriptor: itself, by its name, and every file it includes.
     *
     * @throws IOException when the descriptor or one of its includes cannot be read
     */
    public static PipelineFiles of(Path descriptor) throws IOException {
        PipelineFolder source = new PipelineFolder(descriptor.toAbsolutePath().getParent());
        String main = descriptor.getFileName().toString();
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : DescriptorFiles.of(source, main)) {
            files.put(path, Files.readString(source.folder.resolve(path)));
        }
        return new PipelineFiles(main, files);
    }

    @Override
    public String name() {
        return "the folder " + folder;
    }

    @Override
    public Optional<byte[]> read(String location) throws IOException {
        Path file = folder.resolve(location);
        return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
    }

    @Override
    public Optional<List<String>> list(String inside) {
        return Optional.empty();
    }

    @Override
    public String resolve(String from, String relative) {
        return URI.create(from).resolve(relative).normalize().toString();
    }

    /** A main file and every file of the pipeline, by its path. */
    public record PipelineFiles(String main, Map<String, String> files) {
        public PipelineFiles {
            files = Map.copyOf(files);
        }
    }
}
