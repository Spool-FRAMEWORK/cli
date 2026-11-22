package software.spool.cli.command;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;
import software.spool.infrastructure.provisioning.ProvisioningTarget;

import java.util.Arrays;
import java.util.Iterator;
import java.util.stream.Collectors;

/** Where the Terraform is for, written as the label of a target, such as {@code local} or {@code aws}. */
public final class TargetOption {
    @Option(names = "--target", required = true, converter = Labels.class, paramLabel = "TARGET",
            description = "Where the infrastructure goes: ${COMPLETION-CANDIDATES}.", completionCandidates = Candidates.class)
    private ProvisioningTarget target;

    public ProvisioningTarget target() {
        return target;
    }

    static final class Labels implements ITypeConverter<ProvisioningTarget> {
        @Override
        public ProvisioningTarget convert(String label) {
            return Arrays.stream(ProvisioningTarget.values())
                    .filter(target -> target.label().equalsIgnoreCase(label))
                    .findFirst()
                    .orElseThrow(() -> new TypeConversionException("unknown target '" + label + "': use one of " + labels()));
        }
    }

    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(ProvisioningTarget.values()).map(ProvisioningTarget::label).iterator();
        }
    }

    private static String labels() {
        return Arrays.stream(ProvisioningTarget.values()).map(ProvisioningTarget::label).collect(Collectors.joining(", "));
    }
}
