package software.spool.cli.acceptance.deploy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import software.spool.cli.api.ApiCredentials;
import software.spool.core.port.authentication.Authentication;
import software.spool.core.port.http.HttpMethod;
import software.spool.core.port.http.HttpRequest;
import software.spool.dsl.DescriptorLoader;
import software.spool.dsl.source.TextDescriptorSource;
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

    private final HttpServer server;
    private final HmacSignatureRequestAuthenticator authenticator;
    private final Map<String, Map<String, Object>> deployments = new LinkedHashMap<>();
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
        if (request.method() == HttpMethod.GET && path.equals(DEPLOYMENTS))
            return new Answer(200, JSON.writeValueAsString(Map.of("deployments", new ArrayList<>(deployments.values()))));
        String name = path.substring(DEPLOYMENTS.length() + 1);
        if (request.method() == HttpMethod.DELETE)
            return deployments.remove(name) != null ? new Answer(204, "") : error(404, name + " is not deployed");
        Optional<String> problem = problemOf(body);
        if (problem.isPresent()) return error(400, problem.get());
        boolean isNew = !deployments.containsKey(name);
        deployments.put(name, deployment(name, liveNode, liveNode.isPresent() ? "STARTING" : "WAITING"));
        return new Answer(isNew ? 201 : 200, JSON.writeValueAsString(deployments.get(name)));
    }

    private static Optional<String> problemOf(String descriptor) {
        try {
            new DescriptorLoader(new TextDescriptorSource(descriptor), name -> "${env:" + name + "}")
                    .load(TextDescriptorSource.LOCATION, Optional.empty(), loaded -> loaded);
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
