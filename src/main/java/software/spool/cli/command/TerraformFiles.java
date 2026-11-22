package software.spool.cli.command;

import software.spool.cli.CommandContext;
import software.spool.cli.classpath.ProjectClasses;
import software.spool.infrastructure.provisioning.ProvisioningTarget;
import software.spool.provisioning.TerraformGenerator;

import java.nio.file.Path;

/** Writes the Terraform of a descriptor for a target into a folder, with the classes of the project. */
final class TerraformFiles {
    private TerraformFiles() {}

    static Path write(CommandContext context, DescriptorOptions descriptor, ProvisioningTarget target, Path out)
            throws Exception {
        return new ProjectClasses(context).within(descriptor.classpath(), () -> TerraformGenerator.fromFileSystem()
                .generate(descriptor.file(context), descriptor.profile(context), target)
                .writeTo(context.resolve(out)));
    }
}
