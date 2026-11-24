package software.spool.cli.command;

import picocli.CommandLine.Option;
import software.spool.cli.CommandContext;
import software.spool.cli.api.ApiCredentials;
import software.spool.cli.api.SpoolApiClient;
import software.spool.cli.api.SpoolApiException;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

/** Where the Spool API is, {@code --api} or {@value #API}, and the client that talks to it with the credentials. */
final class ApiOptions {
    static final String API = "SPOOL_API";

    @Option(names = "--api", description = "Where the Spool API is, such as http://localhost:8090. "
            + "SPOOL_API when it is not given.")
    private Optional<URI> api = Optional.empty();

    SpoolApiClient client(CommandContext context) {
        URI address = api.or(() -> Optional.ofNullable(context.environment().apply(API)).filter(value -> !value.isBlank())
                        .map(URI::create))
                .orElseThrow(() -> new SpoolApiException("Give where the Spool API is with --api or " + API));
        return new SpoolApiClient(address, ApiCredentials.fromTheEnvironment(context.environment(), Clock.systemUTC()));
    }
}
