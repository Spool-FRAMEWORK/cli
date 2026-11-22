package software.spool.cli.command;

import software.spool.cli.CommandContext;

/**
 * A command of {@code spool}, found with {@link java.util.ServiceLoader}: a new command is a new class listed in
 * {@code META-INF/services/software.spool.cli.command.SpoolSubcommand}, and nothing else changes.
 */
public interface SpoolSubcommand {
    /** The picocli command, annotated with {@code @Command}, that runs with this context. */
    Object create(CommandContext context);
}
