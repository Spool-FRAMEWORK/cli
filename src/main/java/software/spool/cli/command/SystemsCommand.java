package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import software.spool.cli.CommandContext;
import software.spool.cli.api.DeploymentView;
import software.spool.cli.api.SystemsClient;

import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "systems", description = "Lists the systems of the Spool API, each with its pipelines, their node and "
        + "their state.")
public final class SystemsCommand implements Callable<Integer> {
    private static final String COLUMNS = "  ";
    private static final String NO_NODE = "-";

    private final CommandContext context;

    @Mixin
    private ApiOptions api;

    SystemsCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        List<SystemsClient.SystemView> systems = api.client(context).systems().all();
        if (systems.isEmpty()) context.terminal().say("There are no systems");
        systems.forEach(system -> {
            int pipelines = system.pipelines().size();
            context.terminal().say(system.name() + COLUMNS + pipelines + (pipelines == 1 ? " pipeline" : " pipelines"));
            system.pipelines().forEach(pipeline -> context.terminal().say(COLUMNS + lineOf(pipeline)));
        });
        return 0;
    }

    private static String lineOf(DeploymentView pipeline) {
        String line = String.join(COLUMNS, pipeline.name(), "version " + pipeline.version(),
                pipeline.node().orElse(NO_NODE), pipeline.state());
        return pipeline.error().map(error -> line + COLUMNS + error).orElse(line);
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new SystemsCommand(context);
        }
    }
}
