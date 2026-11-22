package software.spool.cli;

import picocli.CommandLine.Command;

@Command(name = "spool", mixinStandardHelpOptions = true, versionProvider = SpoolVersion.class,
        description = "Validates a Spool descriptor and generates and applies the Terraform of its infrastructure.")
final class SpoolCommand {
}
