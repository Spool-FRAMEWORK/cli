package software.spool.cli.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The systems of the Spool API (ADR-036): deploy a pipeline into a system, list the systems and withdraw a pipeline or
 * a whole system.
 */
public final class SystemsClient {
    private static final String SYSTEMS = "/spool/systems";

    private final ApiRequests requests;

    SystemsClient(ApiRequests requests) {
        this.requests = requests;
    }

    /**
     * Deploys the main file as a pipeline of the system, and the files it includes as files the system shares, creating
     * the system if it does not exist. Shared files the system already has stay, unless the pipeline brings a new text.
     */
    public DeploymentView deploy(String system, String name, PipelineFolder.PipelineFiles pipeline) {
        Map<String, String> shared = new HashMap<>(find(system).map(SystemView::sharedFiles).orElse(Map.of()));
        Map<String, String> brought = new HashMap<>(pipeline.files());
        brought.remove(pipeline.main());
        if (!shared.entrySet().containsAll(brought.entrySet()) || find(system).isEmpty()) {
            shared.putAll(brought);
            requests.send("PUT", SYSTEMS + "/" + system, Optional.of(new SharedFiles(shared)));
        }
        PipelineFolder.PipelineFiles own = new PipelineFolder.PipelineFiles(pipeline.main(),
                Map.of(pipeline.main(), pipeline.files().get(pipeline.main())));
        String answer = requests.send("PUT", SYSTEMS + "/" + system + "/pipelines/" + name, Optional.of(own));
        return SpoolApiClient.viewOf(ApiRequests.read(answer, SpoolApiClient.DeploymentPayload.class));
    }

    public List<SystemView> all() {
        return ApiRequests.read(requests.send("GET", SYSTEMS, Optional.empty()), SystemsPayload.class).systems().stream()
                .map(SystemsClient::viewOf).toList();
    }

    public Optional<SystemView> find(String system) {
        return requests.sendUnlessMissing("GET", SYSTEMS + "/" + system)
                .map(body -> viewOf(ApiRequests.read(body, SystemPayload.class)));
    }

    public void withdrawPipeline(String system, String name) {
        requests.send("DELETE", SYSTEMS + "/" + system + "/pipelines/" + name, Optional.empty());
    }

    public void withdraw(String system) {
        requests.send("DELETE", SYSTEMS + "/" + system, Optional.empty());
    }

    private static SystemView viewOf(SystemPayload payload) {
        return new SystemView(payload.name(), payload.sharedFiles() == null ? Map.of() : payload.sharedFiles(),
                payload.pipelines().stream().map(SpoolApiClient::viewOf).toList());
    }

    /** A system: its name, the files it shares and its pipelines. */
    public record SystemView(String name, Map<String, String> sharedFiles, List<DeploymentView> pipelines) {
    }

    private record SharedFiles(Map<String, String> files) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SystemPayload(String name, Map<String, String> sharedFiles, List<SpoolApiClient.DeploymentPayload> pipelines) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SystemsPayload(List<SystemPayload> systems) {
    }
}
