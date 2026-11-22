package software.spool.cli.acceptance.install;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * A release in a local folder, laid out as the one on GitHub: spool.zip with bin and lib, and spool.zip.sha256. The lib
 * holds the classpath of the tests, every folder of it packed as a jar, which runs the same CLI as the real zip.
 */
final class LocalRelease {
    private static final Path LAUNCHERS = Path.of("src", "dist", "bin");

    private static final String UNIX_LINE = "\n";
    private static final String WINDOWS_LINE = "\r\n";
    private static Path built;

    private LocalRelease() {}

    static void writeTo(Path folder, boolean matchingSha256) throws IOException {
        Files.createDirectories(folder);
        Path zip = Files.copy(zip(), folder.resolve("spool.zip"));
        String sha256 = matchingSha256 ? sha256(zip) : "0".repeat(64);
        Files.writeString(folder.resolve("spool.zip.sha256"), sha256 + "  spool.zip" + UNIX_LINE);
    }

    private static synchronized Path zip() throws IOException {
        if (built == null) built = build(Files.createTempFile("spool", ".zip"));
        return built;
    }

    private static Path build(Path zip) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(out, "bin/spool", Files.readString(LAUNCHERS.resolve("spool")).replace(WINDOWS_LINE, UNIX_LINE).getBytes());
            put(out, "bin/spool.cmd", Files.readAllBytes(LAUNCHERS.resolve("spool.cmd")));
            int index = 0;
            for (Path entry : classpath()) put(out, "lib/" + (index++) + "-" + entry.getFileName() + jarSuffix(entry), bytesOf(entry));
        }
        return zip;
    }

    private static List<Path> classpath() {
        return Stream.of(System.getProperty("java.class.path").split(File.pathSeparator)).map(Path::of)
                .filter(Files::exists).toList();
    }

    private static String jarSuffix(Path entry) {
        return Files.isDirectory(entry) ? ".jar" : "";
    }

    private static byte[] bytesOf(Path entry) throws IOException {
        return Files.isDirectory(entry) ? jarOf(entry) : Files.readAllBytes(entry);
    }

    private static void put(ZipOutputStream out, String name, byte[] content) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(content);
        out.closeEntry();
    }

    private static String sha256(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] jarOf(Path folder) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream jar = new ZipOutputStream(bytes); Stream<Path> files = Files.walk(folder)) {
            files.filter(Files::isRegularFile).forEach(file -> add(jar, folder, file));
        }
        return bytes.toByteArray();
    }

    private static void add(ZipOutputStream jar, Path folder, Path file) {
        try {
            jar.putNextEntry(new ZipEntry(folder.relativize(file).toString().replace(File.separatorChar, '/')));
            Files.copy(file, (OutputStream) jar);
            jar.closeEntry();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
