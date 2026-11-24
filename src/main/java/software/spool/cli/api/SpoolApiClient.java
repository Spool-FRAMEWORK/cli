package software.spool.cli.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import software.spool.core.model.spool.NodeLabels;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The deployments of the Spool API (ADR-031): deploy a descriptor under a name, list them and withdraw one. Fields of
 * an answer the CLI does not use are ignored, so a newer API with more of them still works.
 */
public final class SpoolApiClient {
    private static final String DEPLOYMENTS = "/spool/deployments";

    private final ApiRequests requests;

    public SpoolApiClient(URI api, ApiCredentials credentials) {
        this.requests = new ApiRequests(api, credentials);
    }

    /**
     * Deploys a pipeline as its main file and every file it includes, asking for a node with every one of the labels
     * (ADR-036).
     */
    public DeploymentView deploy(String name, PipelineFolder.PipelineFiles pipeline, NodeLabels labels) {
        DeploymentRequest request = new DeploymentRequest(pipeline.main(), pipeline.files(), labels.asList());
        return viewOf(ApiRequests.read(requests.send("PUT", DEPLOYMENTS + "/" + name, Optional.of(request)), DeploymentPayload.class));
    }

    public List<DeploymentView> deployments() {
        return ApiRequests.read(requests.send("GET", DEPLOYMENTS, Optional.empty()), DeploymentsPayload.class)
                .deployments().stream().map(SpoolApiClient::viewOf).toList();
    }

    public void withdraw(String name) {
        requests.send("DELETE", DEPLOYMENTS + "/" + name, Optional.empty());
    }

    public SystemsClient systems() {
        return new SystemsClient(requests);
    }

    static DeploymentView viewOf(DeploymentPayload payload) {
        return new DeploymentView(payload.name(), payload.version(),
                payload.labels() == null ? List.of() : List.copyOf(payload.labels()), payload.state(),
                Optional.ofNullable(payload.node()), Optional.ofNullable(payload.error()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DeploymentPayload(String name, int version, List<String> labels, String state, String node, String error) {
    }

    private record DeploymentRequest(String main, Map<String, String> files, List<String> labels) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeploymentsPayload(List<DeploymentPayload> deployments) {
    }
}
