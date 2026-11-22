package software.spool.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Where the CLI talks to whoever runs it: what it says, what goes wrong, and the answers to its questions. */
public record Terminal(BufferedReader input, PrintWriter output, PrintWriter error) {

    public static Terminal ofTheConsole() {
        return new Terminal(new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)),
                new PrintWriter(System.out, true, StandardCharsets.UTF_8),
                new PrintWriter(System.err, true, StandardCharsets.UTF_8));
    }

    public void say(String message) {
        output.println(message);
        output.flush();
    }

    public void fail(String message) {
        error.println(message);
        error.flush();
    }

    /** Asks a yes-or-no question; only an explicit yes counts, so no answer at all is a no. */
    public boolean confirms(String question) {
        output.print(question + " [y/N] ");
        output.flush();
        return answer().map(String::strip).filter(answer -> answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes"))
                .isPresent();
    }

    private Optional<String> answer() {
        try {
            return Optional.ofNullable(input.readLine());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
