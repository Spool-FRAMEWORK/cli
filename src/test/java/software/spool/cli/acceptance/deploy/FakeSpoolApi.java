package software.spool.cli.acceptance.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.spool.cli.api.ApiCredentials;
import software.spool.core.port.authentication.Authentication;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.source.InMemoryDescriptorSource;
import software.spool.infrastructure.adapter.authentication.hmac.HmacSignatureRequestAuthenticator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Spool API that answers the deployment routes as the real one does: it checks the signature with the same provider,
 * loads the descriptor with the DSL and keeps the deployments on its one live node, if it has one. It keeps every
 * request it receives.
 */
final class FakeSpoolApi {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEPLOYMENTS = "/spool/deployments";
    private static final String SYSTEMS = "/spool/systems";

    private final HttpServer server;
    private final HmacSignatureRequestAuthenticator authenticator;
    private final Map<String, Map<String, Object>> deployments = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> systems = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> pipelines = new LinkedHashMap<>();
    private final List<Received> received = new CopyOnWriteArrayList<>();
    private Optional<String> liveNode;

    FakeSpoolApi(String secret, String liveNode) throws IOException {
        this.liveNode = Optional.of(liveNode);
        authenticator = new HmacSignatureRequestAuthenticator("deployers", ApiCredentials.SIGNATURE, secret,
                Optional.of(ApiCredentials.TIMESTAMP), Duration.ofMinutes(5), Set.of("deployer"), Clock.systemUTC());
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", this::answer);
        server.start();
    }

    URI address() {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    void stop() {
        server.stop(0);
    }

    void noLiveNode() {
        liveNode = Optional.empty();
    }

    void has(String name, Optional<String> node, String state) {
        deployments.put(name, deployment(name, node, state));
    }

    List<Received> received() {
        return List.copyOf(received);
    }

    private synchronized void answer(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        HttpRequest request = new HttpRequest(HttpMethod.valueOf(exchange.getRequestMethod()), exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders(), body);
        boolean signed = authenticator.authenticate(request) instanceof Authentication.Accepted;
        received.add(new Received(request.method().name(), request.path(), new String(body, StandardCharsets.UTF_8), signed));
        Answer answer = signed ? route(request, new String(body, StandardCharsets.UTF_8)) : new Answer(401, "");
        byte[] bytes = answer.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(answer.status(), bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Answer route(HttpRequest request, String body) throws IOException {
        String path = request.path();
        if (path.startsWith(SYSTEMS)) return systemRoute(request, body);
        if (request.method() == HttpMethod.GET && path.equals(DEPLOYMENTS))
            return new Answer(200, JSON.writeValueAsString(Map.of("deployments", new ArrayList<>(deployments.values()))));
        String name = path.substring(DEPLOYMENTS.length() + 1);
        if (request.method() == HttpMethod.DELETE)
            return deployments.remove(name) != null ? new Answer(204, "") : error(404, name + " is not deployed");
        Optional<String> problem = problemOf(JSON.readTree(body));
        if (problem.isPresent()) return error(400, problem.get());
        boolean isNew = !deployments.containsKey(name);
        deployments.put(name, deployment(name, liveNode, liveNode.isPresent() ? "STARTING" : "WAITING"));
        return new Answer(isNew ? 201 : 200, JSON.writeValueAsString(deployments.get(name)));
    }

    /** The routes of the systems, as the real API answers them: a system keeps shared files and its pipelines. */
    private Answer systemRoute(HttpRequest request, String body) throws IOException {
        String[] parts = request.path().substring(SYSTEMS.length()).split("/");
        if (parts.length <= 1) return new Answer(200, JSON.writeValueAsString(Map.of("systems",
                systems.keySet().stream().map(this::systemOf).toList())));
        String system = parts[1];
        if (parts.length == 2) {
            if (request.method() == HttpMethod.GET)
                return systems.containsKey(system) ? new Answer(200, JSON.writeValueAsString(systemOf(system)))
                        : error(404, system + " does not exist");
            if (request.method() == HttpMethod.DELETE) {
                pipelines.remove(system);
                return systems.remove(system) != null ? new Answer(204, "") : error(404, system + " does not exist");
            }
            boolean isNew = !systems.containsKey(system);
            Map<String, String> files = new HashMap<>();
            JSON.readTree(body).get("files").fields().forEachRemaining(file -> files.put(file.getKey(), file.getValue().asText()));
            systems.put(system, files);
            return new Answer(isNew ? 201 : 200, JSON.writeValueAsString(systemOf(system)));
        }
        String pipeline = parts[3];
        if (request.method() == HttpMethod.DELETE)
            return pipelines.getOrDefault(system, new HashMap<>()).remove(pipeline) != null ? new Answer(204, "")
                    : error(404, pipeline + " is not a pipeline of " + system);
        if (!systems.containsKey(system)) return error(404, system + " does not exist");
        JsonNode sent = JSON.readTree(body);
        Map<String, String> files = new HashMap<>(systems.get(system));
        sent.get("files").fields().forEachRemaining(file -> files.put(file.getKey(), file.getValue().asText()));
        Optional<String> problem = problemOf(sent.get("main").asText(), files);
        if (problem.isPresent()) return error(400, problem.get());
        boolean isNew = !pipelines.computeIfAbsent(system, ignored -> new LinkedHashMap<>()).containsKey(pipeline);
        pipelines.get(system).put(pipeline, deployment(pipeline, liveNode, liveNode.isPresent() ? "STARTING" : "WAITING"));
        return new Answer(isNew ? 201 : 200, JSON.writeValueAsString(pipelines.get(system).get(pipeline)));
    }

    private Map<String, Object> systemOf(String system) {
        return Map.of("name", system, "sharedFiles", systems.get(system),
                "pipelines", new ArrayList<>(pipelines.getOrDefault(system, Map.of()).values()));
    }

    Map<String, String> sharedFilesOf(String system) {
        return systems.getOrDefault(system, Map.of());
    }

    Optional<Map<String, Object>> pipelineOf(String system, String pipeline) {
        return Optional.ofNullable(pipelines.getOrDefault(system, Map.of()).get(pipeline));
    }

    private static Optional<String> problemOf(String main, Map<String, String> files) {
        try {
            new DescriptorLoader(new InMemoryDescriptorSource(files), name -> "${env:" + name + "}")
                    .load(main, Optional.empty(), loaded -> loaded);
            return Optional.empty();
        } catch (IOException invalid) {
            return Optional.of(invalid.getMessage());
        }
    }

    private static Optional<String> problemOf(JsonNode pipeline) {
        Map<String, String> files = new HashMap<>();
        pipeline.get("files").fields().forEachRemaining(file -> files.put(file.getKey(), file.getValue().asText()));
        try {
            new DescriptorLoader(new InMemoryDescriptorSource(files), name -> "${env:" + name + "}")
                    .load(pipeline.get("main").asText(), Optional.empty(), loaded -> loaded);
            return Optional.empty();
        } catch (IOException invalid) {
            return Optional.of(invalid.getMessage());
        }
    }

    private static Map<String, Object> deployment(String name, Optional<String> node, String state) {
        Map<String, Object> deployment = new HashMap<>();
        deployment.put("name", name);
        deployment.put("owner", "deployers");
        deployment.put("ownerProvider", "deployers");
        deployment.put("version", 1);
        deployment.put("state", state);
        deployment.put("node", node.orElse(null));
        deployment.put("error", null);
        return deployment;
    }

    private static Answer error(int status, String message) throws IOException {
        return new Answer(status, JSON.writeValueAsString(Map.of("error", message)));
    }

    private record Answer(int status, String body) {
    }

    record Received(String method, String path, String body, boolean signed) {
    }
}
