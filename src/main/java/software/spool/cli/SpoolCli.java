package software.spool.cli;

import picocli.CommandLine;
import software.spool.cli.command.SpoolSubcommand;

import java.util.ServiceLoader;

/** Builds the {@code spool} command with every command found, and runs it. */
public final class SpoolCli {
    public static final int FAILED = 1;

    private SpoolCli() {}

    public static int run(String[] arguments, CommandContext context) {
        return commandLine(context).execute(arguments);
    }

    private static CommandLine commandLine(CommandContext context) {
        CommandLine commandLine = new CommandLine(new SpoolCommand());
        ServiceLoader.load(SpoolSubcommand.class).forEach(command -> commandLine.addSubcommand(command.create(context)));
        return commandLine
                .setOut(context.terminal().output())
                .setErr(context.terminal().error())
                .setExecutionExceptionHandler((exception, failed, parseResult) -> {
                    context.terminal().fail(exception.getMessage());
                    return FAILED;
                });
    }
}
