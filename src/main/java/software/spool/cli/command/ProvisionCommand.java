package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import software.spool.cli.CommandContext;
import software.spool.cli.SpoolCli;
import software.spool.cli.program.ExternalProgram;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Generates the Terraform of a descriptor and applies it. It only applies after an explicit yes, because in the cloud
 * it creates resources that cost money; that yes is why Terraform is then told not to ask again.
 */
@Command(name = "provision", description = "Generates the Terraform of a descriptor and applies it, after a confirmation.")
public final class ProvisionCommand implements Callable<Integer> {
    private final CommandContext context;

    @Mixin
    private DescriptorOptions descriptor;

    @Mixin
    private TargetOption target;

    @Option(names = "--out", defaultValue = ".spool/terraform", paramLabel = "FOLDER",
            description = "Where to write the Terraform before applying it. ${DEFAULT-VALUE} by default.")
    private Path out;

    @Option(names = "--yes", description = "Applies without asking.")
    private boolean confirmed;

    ProvisionCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() throws Exception {
        ExternalProgram terraform = context.programs().find("terraform").orElseThrow(() -> new IllegalStateException(
                "Terraform is not installed: install it, or set TERRAFORM_BINARY to its binary"));
        Path folder = TerraformFiles.write(context, descriptor, target.target(), out);
        if (!confirmed && !context.terminal().confirms("Apply the Terraform for " + target.target().label() + " in " + out + "?")) {
            context.terminal().say("Nothing applied");
            return SpoolCli.FAILED;
        }
        return apply(terraform, folder);
    }

    private static int apply(ExternalProgram terraform, Path folder) {
        int initialized = terraform.run(folder, List.of("init", "-input=false"));
        return initialized != 0 ? initialized : terraform.run(folder, List.of("apply", "-auto-approve", "-input=false"));
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new ProvisionCommand(context);
        }
    }
}
