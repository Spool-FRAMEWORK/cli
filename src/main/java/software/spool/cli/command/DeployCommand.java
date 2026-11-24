package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import software.spool.cli.CommandContext;
import software.spool.cli.api.DeploymentView;
import software.spool.cli.api.PipelineFolder;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;

@Command(name = "deploy", description = "Deploys a descriptor and every file it includes in the cluster of the Spool API.")
public final class DeployCommand implements Callable<Integer> {
    private static final String DESCRIPTOR_SUFFIX = ".spool.yaml";
    private static final String YAML_SUFFIX = ".yaml";

    private final CommandContext context;

    @Parameters(index = "0", paramLabel = "DESCRIPTOR", description = "The descriptor, such as orders.spool.yaml.")
    private Path descriptor;

    @Option(names = "--name", description = "The name of the deployment. By default, the name of the file without "
            + ".spool.yaml.")
    private Optional<String> name = Optional.empty();

    @Mixin
    private ApiOptions api;

    DeployCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() throws Exception {
        PipelineFolder.PipelineFiles pipeline = PipelineFolder.of(context.resolve(descriptor));
        DeploymentView deployed = api.client(context).deploy(name.orElseGet(this::nameOfTheFile), pipeline);
        context.terminal().say(deployed.waits()
                ? deployed.name() + " is deployed and waits for a live node"
                : deployed.name() + " is deployed on " + deployed.node().orElse("?") + ", in its version " + deployed.version());
        return 0;
    }

    private String nameOfTheFile() {
        String file = descriptor.getFileName().toString();
        if (file.endsWith(DESCRIPTOR_SUFFIX)) return file.substring(0, file.length() - DESCRIPTOR_SUFFIX.length());
        if (file.endsWith(YAML_SUFFIX)) return file.substring(0, file.length() - YAML_SUFFIX.length());
        return file;
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new DeployCommand(context);
        }
    }
}
