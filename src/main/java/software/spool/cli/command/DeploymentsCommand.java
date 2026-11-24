package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import software.spool.cli.CommandContext;
import software.spool.cli.api.DeploymentView;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "deployments", description = "Lists the deployments of the Spool API, with their node and their state.")
public final class DeploymentsCommand implements Callable<Integer> {
    private static final String COLUMNS = "  ";
    private static final String NO_NODE = "-";

    private final CommandContext context;

    @Mixin
    private ApiOptions api;

    DeploymentsCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        List<DeploymentView> deployments = api.client(context).deployments();
        if (deployments.isEmpty()) context.terminal().say("Nothing is deployed");
        deployments.forEach(deployment -> context.terminal().say(lineOf(deployment)));
        return 0;
    }

    private static String lineOf(DeploymentView deployment) {
        String line = String.join(COLUMNS, deployment.name(), "version " + deployment.version(),
                deployment.node().orElse(NO_NODE), deployment.state());
        return deployment.error().map(error -> line + COLUMNS + error).orElse(line);
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new DeploymentsCommand(context);
        }
    }
}
