package software.spool.cli.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import software.spool.core.adapter.jackson.PayloadDeserializerFactory;
import software.spool.core.adapter.jackson.RecordSerializerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * The requests of the CLI to the Spool API, each with the credentials, and their answers read as JSON. An answer that
 * is not a success becomes a {@link SpoolApiException} with the error the API gave.
 */
final class ApiRequests {
    private static final int NOT_FOUND = 404;
    private static final String JSON = "application/json";

    private final URI api;
    private final ApiCredentials credentials;
    private final HttpClient http = HttpClient.newHttpClient();

    ApiRequests(URI api, ApiCredentials credentials) {
        this.api = api;
        this.credentials = credentials;
    }

    String send(String method, String path, Optional<Object> body) {
        HttpResponse<String> response = exchange(method, path, body);
        if (response.statusCode() >= 300) throw failureOf(response);
        return response.body();
    }

    /** The answer, or nothing when the API says there is no such thing. */
    Optional<String> sendUnlessMissing(String method, String path) {
        HttpResponse<String> response = exchange(method, path, Optional.empty());
        if (response.statusCode() == NOT_FOUND) return Optional.empty();
        if (response.statusCode() >= 300) throw failureOf(response);
        return Optional.of(response.body());
    }

    static <T> T read(String body, Class<T> type) {
        return PayloadDeserializerFactory.json().as(type).deserialize(body.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> exchange(String method, String path, Optional<Object> body) {
        byte[] bytes = body.map(content -> RecordSerializerFactory.record().serialize(content)).orElse(new byte[0]);
        HttpRequest.Builder request = HttpRequest.newBuilder(api.resolve(path))
                .method(method, bytes.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(bytes))
                .header("Content-Type", JSON);
        credentials.headersFor(bytes).forEach(request::header);
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SpoolApiException("Could not reach the Spool API at " + api + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpoolApiException("Interrupted while calling the Spool API", e);
        }
    }

    private static SpoolApiException failureOf(HttpResponse<String> response) {
        return new SpoolApiException("The Spool API answered " + response.statusCode() + ": " + errorOf(response));
    }

    private static String errorOf(HttpResponse<String> response) {
        if (response.body().isBlank()) return "no reason given";
        try {
            return Optional.ofNullable(read(response.body(), ErrorPayload.class).error()).orElse(response.body());
        } catch (RuntimeException notAnError) {
            return response.body();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorPayload(String error) {
    }
}
