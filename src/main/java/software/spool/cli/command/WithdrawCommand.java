package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;
import software.spool.cli.CommandContext;

import java.util.concurrent.Callable;

@Command(name = "withdraw", description = "Withdraws a deployment from the Spool API, so its node stops it.")
public final class WithdrawCommand implements Callable<Integer> {
    private final CommandContext context;

    @Parameters(index = "0", paramLabel = "NAME", description = "The name of the deployment.")
    private String name;

    @Mixin
    private ApiOptions api;

    WithdrawCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        api.client(context).withdraw(name);
        context.terminal().say(name + " is withdrawn");
        return 0;
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new WithdrawCommand(context);
        }
    }
}
