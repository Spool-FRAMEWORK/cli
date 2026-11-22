package software.spool.cli.acceptance.install;

import org.opentest4j.TestAbortedException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** An install script run in a home of its own, against a release in a local folder. */
public class InstallUnderTest {
    private static final List<String> TOOLS_OF_THE_SCRIPTS = List.of("curl", "unzip", "sh");
    private final Path home;
    private final Path release;
    private final Map<String, String> environment = new HashMap<>(System.getenv());
    private InstallScript script;
    private String output = "";
    private int exitCode = -1;

    public InstallUnderTest() throws IOException {
        Path folder = Files.createTempDirectory("spool-install-test");
        home = Files.createDirectories(folder.resolve("home"));
        release = folder.resolve("release");
        environment.put("HOME", home.toString());
        environment.put("SPOOL_HOME", installation().toString());
        environment.put("SPOOL_DOWNLOAD_URL", release.toUri().toString().replaceAll("/$", ""));
    }

    public void release(boolean matchingSha256) throws IOException {
        LocalRelease.writeTo(release, matchingSha256);
    }

    public void set(String name, String value) {
        environment.put(name, value);
    }

    public void withoutJava() {
        List<String> kept = Arrays.stream(environment.getOrDefault("PATH", "").split(File.pathSeparator))
                .filter(folder -> !hasJava(Path.of(folder)))
                .toList();
        List<String> removed = Arrays.stream(environment.getOrDefault("PATH", "").split(File.pathSeparator))
                .filter(folder -> !kept.contains(folder)).toList();
        if (removed.stream().anyMatch(InstallUnderTest::hasAToolOfTheScripts))
            throw new TestAbortedException("Java shares a folder with the tools of the scripts: " + removed);
        environment.put("PATH", String.join(File.pathSeparator, kept));
        environment.remove("JAVA_HOME");
    }

    public void run(InstallScript script) throws Exception {
        this.script = script;
        Process process = start(script.run(script.shell(), script.file()));
        output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(5, TimeUnit.MINUTES)) throw new IllegalStateException(script + " did not end");
        exitCode = process.exitValue();
    }

    public String launcherVersion() throws Exception {
        Process process = new ProcessBuilder(script.runLauncher(installation(), script.shell(), "--version"))
                .redirectErrorStream(true).start();
        String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        process.waitFor(1, TimeUnit.MINUTES);
        return version;
    }

    public String userPathOfWindows() throws Exception {
        Process process = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                "[Environment]::GetEnvironmentVariable('Path','User')").redirectErrorStream(true).start();
        String path = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        process.waitFor(1, TimeUnit.MINUTES);
        return path;
    }

    public Path home() {
        return home;
    }

    public Path installation() {
        return home.resolve(".spool");
    }

    public InstallScript script() {
        return script;
    }

    public String output() {
        return output;
    }

    public int exitCode() {
        return exitCode;
    }

    private Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(home.toFile()).redirectErrorStream(true);
        builder.environment().clear();
        builder.environment().putAll(environment);
        Process process = builder.start();
        process.getOutputStream().close();
        return process;
    }

    private static boolean hasJava(Path folder) {
        return Files.isRegularFile(folder.resolve("java")) || Files.isRegularFile(folder.resolve("java.exe"));
    }

    private static boolean hasAToolOfTheScripts(String folder) {
        return TOOLS_OF_THE_SCRIPTS.stream().anyMatch(tool -> Files.isRegularFile(Path.of(folder, tool))
                || Files.isRegularFile(Path.of(folder, tool + ".exe")));
    }
}
