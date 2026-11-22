package software.spool.cli.program;

import java.util.Optional;

/** Finds the programs installed on the machine by their name. */
public interface InstalledPrograms {
    Optional<ExternalProgram> find(String name);
}
