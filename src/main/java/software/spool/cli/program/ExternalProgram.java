package software.spool.cli.program;

import java.nio.file.Path;
import java.util.List;

/** A program installed on the machine, such as Terraform or Maven. */
public interface ExternalProgram {
    /** Runs the program in a folder until it ends, and returns its exit code. */
    int run(Path folder, List<String> arguments);
}
