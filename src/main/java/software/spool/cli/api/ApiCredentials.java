package software.spool.cli.api;

import software.spool.infrastructure.adapter.authentication.hmac.HmacSigner;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * How the CLI proves who it is to the Spool API, read from the environment and never from an argument: the token of
 * {@value #TOKEN}, sent as a bearer token for an {@code OIDC} provider, or else the secret of {@value #SECRET}, which
 * signs each request with the headers {@value #SIGNATURE} and {@value #TIMESTAMP} for an {@code HMAC_SIGNATURE} one.
 */
public sealed interface ApiCredentials {
    String TOKEN = "SPOOL_API_TOKEN";
    String SECRET = "SPOOL_API_SECRET";
    String SIGNATURE = "X-Spool-Signature";
    String TIMESTAMP = "X-Spool-Timestamp";

    /** The headers that go with a request of this body. */
    Map<String, String> headersFor(byte[] body);

    /** @throws SpoolApiException when the environment has neither variable */
    static ApiCredentials fromTheEnvironment(UnaryOperator<String> environment, Clock clock) {
        Optional<String> token = valueOf(environment, TOKEN);
        if (token.isPresent()) return new Bearer(token.get());
        return valueOf(environment, SECRET).<ApiCredentials>map(secret -> new Signed(secret, clock))
                .orElseThrow(() -> new SpoolApiException("Set " + SECRET + " or " + TOKEN
                        + " to a credential the Spool API accepts; neither is read from an argument"));
    }

    private static Optional<String> valueOf(UnaryOperator<String> environment, String name) {
        return Optional.ofNullable(environment.apply(name)).filter(value -> !value.isBlank());
    }

    record Bearer(String token) implements ApiCredentials {
        @Override
        public Map<String, String> headersFor(byte[] body) {
            return Map.of("Authorization", "Bearer " + token);
        }

        @Override
        public String toString() {
            return "Bearer[token=***]";
        }
    }

    record Signed(String secret, Clock clock) implements ApiCredentials {
        @Override
        public Map<String, String> headersFor(byte[] body) {
            return new HmacSigner(SIGNATURE, secret, Optional.of(TIMESTAMP), clock).headersFor(body);
        }

        @Override
        public String toString() {
            return "Signed[secret=***]";
        }
    }
}
