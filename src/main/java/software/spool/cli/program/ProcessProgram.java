package software.spool.cli.program;

import software.spool.cli.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Runs a binary as a process, and shows what it writes in the terminal. */
final class ProcessProgram implements ExternalProgram {
    private final Path binary;
    private final Terminal terminal;

    ProcessProgram(Path binary, Terminal terminal) {
        this.binary = binary;
        this.terminal = terminal;
    }

    @Override
    public int run(Path folder, List<String> arguments) {
        List<String> command = new ArrayList<>(List.of(binary.toString()));
        command.addAll(arguments);
        try {
            Process process = new ProcessBuilder(command).directory(folder.toFile()).redirectErrorStream(true).start();
            process.getOutputStream().close();
            showTheOutputOf(process);
            return process.waitFor();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not run " + binary + ": " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running " + binary, exception);
        }
    }

    private void showTheOutputOf(Process process) throws IOException {
        try (BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output.lines().forEach(terminal::say);
        }
    }
}
