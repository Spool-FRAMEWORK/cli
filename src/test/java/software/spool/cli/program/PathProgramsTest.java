package software.spool.cli.program;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.spool.cli.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathProgramsTest {
    @TempDir
    Path folder;

    @Test
    void find_programInItsBinaryVariable_isFound() throws IOException {
        Path binary = Files.createFile(folder.resolve("my-terraform"));

        assertThat(programs(Map.of("TERRAFORM_BINARY", binary.toString())).find("terraform")).isPresent();
    }

    @Test
    void find_programOnThePathWithAWindowsExtension_isFound() throws IOException {
        Files.createFile(folder.resolve("mvn.cmd"));

        assertThat(programs(Map.of("PATH", folder.toString(), "PATHEXT", ".EXE;.CMD")).find("mvn")).isPresent();
    }

    @Test
    void binaryOf_bareFileNextToItsWindowsCommand_isTheCommand() throws IOException {
        Files.createFile(folder.resolve("mvn"));
        Path command = Files.createFile(folder.resolve("mvn.cmd"));

        assertThat(programs(Map.of("PATH", folder.toString(), "PATHEXT", ".EXE;.CMD")).binaryOf("mvn")).contains(command);
    }

    @Test
    void find_programNowhere_isEmpty() {
        assertThat(programs(Map.of("PATH", folder.toString())).find("terraform")).isEmpty();
    }

    private static PathPrograms programs(Map<String, String> environment) {
        return new PathPrograms(environment::get, Terminal.ofTheConsole());
    }
}
