package software.spool.cli.acceptance.install;

import org.opentest4j.TestAbortedException;
import software.spool.cli.Terminal;
import software.spool.cli.program.PathPrograms;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** An install script, the shell that runs it and the launcher it installs. */
enum InstallScript {
    SH("install.sh", "sh") {
        @Override
        List<String> runLauncher(Path installation, Path shell, String argument) {
            return List.of(shell.toString(), installation.resolve("bin/spool").toString(), argument);
        }

        @Override
        List<String> run(Path shell, Path script) {
            return List.of(shell.toString(), script.toString());
        }
    },
    PS1("install.ps1", "powershell") {
        @Override
        List<String> runLauncher(Path installation, Path shell, String argument) {
            return List.of("cmd.exe", "/c", installation.resolve("bin").resolve("spool.cmd").toString(), argument);
        }

        @Override
        List<String> run(Path shell, Path script) {
            return List.of(shell.toString(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString());
        }
    };

    private static final Path SCRIPTS = Path.of("install");
    private final String fileName;
    private final String shellName;

    InstallScript(String fileName, String shellName) {
        this.fileName = fileName;
        this.shellName = shellName;
    }

    static InstallScript named(String fileName) {
        for (InstallScript script : values()) if (script.fileName.equals(fileName)) return script;
        throw new IllegalArgumentException("No install script " + fileName);
    }

    Path shell() {
        return shellOnTheMachine().orElseThrow(() -> new TestAbortedException(shellName + " is not on this machine"));
    }

    Path file() {
        return SCRIPTS.resolve(fileName).toAbsolutePath();
    }

    abstract List<String> run(Path shell, Path script);

    abstract List<String> runLauncher(Path installation, Path shell, String argument);

    private Optional<Path> shellOnTheMachine() {
        return new PathPrograms(System::getenv, Terminal.ofTheConsole()).binaryOf(shellName);
    }
}
