package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import software.spool.cli.CommandContext;

import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "terraform", description = "Writes the Terraform of the infrastructure of a descriptor, without applying it.")
public final class TerraformCommand implements Callable<Integer> {
    private final CommandContext context;

    @Mixin
    private DescriptorOptions descriptor;

    @Mixin
    private TargetOption target;

    @Option(names = "--out", defaultValue = "infrastructure", paramLabel = "FOLDER",
            description = "Where to write the Terraform. ${DEFAULT-VALUE} by default.")
    private Path out;

    TerraformCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() throws Exception {
        TerraformFiles.write(context, descriptor, target.target(), out);
        context.terminal().say("Terraform for " + target.target().label() + " written to " + out);
        return 0;
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new TerraformCommand(context);
        }
    }
}
