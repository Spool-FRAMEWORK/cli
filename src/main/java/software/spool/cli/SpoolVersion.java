package software.spool.cli;

import picocli.CommandLine.IVersionProvider;

import java.util.Optional;

/** The version written in the manifest of the jar, or {@code development} when run from the classes. */
final class SpoolVersion implements IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[]{"spool " + Optional.ofNullable(SpoolVersion.class.getPackage().getImplementationVersion())
                .orElse("development")};
    }
}
