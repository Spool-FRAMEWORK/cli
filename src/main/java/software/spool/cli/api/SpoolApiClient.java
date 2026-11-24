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
import java.util.List;
import java.util.Optional;

/**
 * The deployments of the Spool API (ADR-031): deploy a descriptor under a name, list them and withdraw one. An answer
 * that is not the expected one becomes a {@link SpoolApiException} with the error the API gave. Fields of an answer
 * the CLI does not use are ignored, so a newer API with more of them still works.
 */
public final class SpoolApiClient {
    private static final String DEPLOYMENTS = "/spool/deployments";

    private final URI api;
    private final ApiCredentials credentials;
    private final HttpClient http = HttpClient.newHttpClient();

    public SpoolApiClient(URI api, ApiCredentials credentials) {
        this.api = api;
        this.credentials = credentials;
    }

    /** Deploys a pipeline as its main file and every file it includes (ADR-036). */
    public DeploymentView deploy(String name, PipelineFolder.PipelineFiles pipeline) {
        byte[] body = RecordSerializerFactory.<PipelineFolder.PipelineFiles>record().serialize(pipeline);
        return viewOf(read(send("PUT", DEPLOYMENTS + "/" + name, body, "application/json"), DeploymentPayload.class));
    }

    public List<DeploymentView> deployments() {
        return read(send("GET", DEPLOYMENTS, new byte[0], "application/json"), DeploymentsPayload.class)
                .deployments().stream().map(SpoolApiClient::viewOf).toList();
    }

    public void withdraw(String name) {
        send("DELETE", DEPLOYMENTS + "/" + name, new byte[0], "application/json");
    }

    private String send(String method, String path, byte[] body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(api.resolve(path))
                .method(method, body.length == 0 ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body))
                .header("Content-Type", contentType);
        credentials.headersFor(body).forEach(request::header);
        HttpResponse<String> response = sendOrFail(request.build());
        if (response.statusCode() >= 300)
            throw new SpoolApiException("The Spool API answered " + response.statusCode() + ": " + errorOf(response));
        return response.body();
    }

    private HttpResponse<String> sendOrFail(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new SpoolApiException("Could not reach the Spool API at " + api + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpoolApiException("Interrupted while calling the Spool API", e);
        }
    }

    private static String errorOf(HttpResponse<String> response) {
        if (response.body().isBlank()) return "no reason given";
        try {
            return Optional.ofNullable(read(response.body(), ErrorPayload.class).error()).orElse(response.body());
        } catch (RuntimeException notAnError) {
            return response.body();
        }
    }

    private static <T> T read(String body, Class<T> type) {
        return PayloadDeserializerFactory.json().as(type).deserialize(body.getBytes(StandardCharsets.UTF_8));
    }

    private static DeploymentView viewOf(DeploymentPayload payload) {
        return new DeploymentView(payload.name(), payload.version(), payload.state(), Optional.ofNullable(payload.node()),
                Optional.ofNullable(payload.error()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeploymentPayload(String name, int version, String state, String node, String error) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeploymentsPayload(List<DeploymentPayload> deployments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ErrorPayload(String error) {
    }
}
