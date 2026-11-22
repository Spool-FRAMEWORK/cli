package software.spool.cli;

/** The entry point of the {@code spool} command. */
public final class Spool {
    private Spool() {}

    public static void main(String[] arguments) {
        System.exit(SpoolCli.run(arguments, CommandContext.ofTheMachine()));
    }
}
