package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.spool.cli.CommandContext;
import software.spool.cli.api.SpoolApiClient;
import software.spool.cli.api.SpoolApiException;

import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "withdraw", description = "Withdraws a deployment, a pipeline of a system or a whole system from the "
        + "Spool API, so their nodes stop them.")
public final class WithdrawCommand implements Callable<Integer> {
    private final CommandContext context;

    @Parameters(index = "0", arity = "0..1", paramLabel = "NAME", description = "The name of the deployment, or of the "
            + "pipeline of --system. Without it, --system withdraws the whole system.")
    private Optional<String> name = Optional.empty();

    @Option(names = "--system", description = "The system the pipeline belongs to.")
    private Optional<String> system = Optional.empty();

    @Mixin
    private ApiOptions api;

    WithdrawCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        SpoolApiClient client = api.client(context);
        if (system.isEmpty()) {
            String deployment = name.orElseThrow(() -> new SpoolApiException("Give the deployment to withdraw, or --system"));
            client.withdraw(deployment);
            context.terminal().say(deployment + " is withdrawn");
        } else if (name.isPresent()) {
            client.systems().withdrawPipeline(system.get(), name.get());
            context.terminal().say(name.get() + " of " + system.get() + " is withdrawn");
        } else {
            int pipelines = client.systems().find(system.get()).map(found -> found.pipelines().size()).orElse(0);
            client.systems().withdraw(system.get());
            context.terminal().say(system.get() + " is withdrawn, with " + pipelines + (pipelines == 1 ? " pipeline" : " pipelines"));
        }
        return 0;
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new WithdrawCommand(context);
        }
    }
}
