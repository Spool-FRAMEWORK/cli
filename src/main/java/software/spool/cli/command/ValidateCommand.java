package software.spool.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import software.spool.cli.CommandContext;
import software.spool.cli.classpath.ProjectClasses;
import software.spool.dsl.SpoolNodeDSL;

import java.util.concurrent.Callable;

@Command(name = "validate", description = "Checks a descriptor, building every module without starting it.")
public final class ValidateCommand implements Callable<Integer> {
    private final CommandContext context;

    @Mixin
    private DescriptorOptions descriptor;

    ValidateCommand(CommandContext context) {
        this.context = context;
    }

    @Override
    public Integer call() throws Exception {
        int modules = new ProjectClasses(context).within(descriptor.classpath(), () -> descriptor.load(context, node -> {
            SpoolNodeDSL.fromDescriptor(node);
            return node.declaredModules().size();
        }));
        context.terminal().say(descriptor.name() + " is valid: " + modules + (modules == 1 ? " module" : " modules"));
        return 0;
    }

    public static final class Factory implements SpoolSubcommand {
        @Override
        public Object create(CommandContext context) {
            return new ValidateCommand(context);
        }
    }
}
