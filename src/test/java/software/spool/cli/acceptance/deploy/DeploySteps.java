package software.spool.cli.acceptance.deploy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import software.spool.cli.acceptance.CliUnderTest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class DeploySteps {
    private static final String API_PLACEHOLDER = "{api}";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final CliUnderTest cli;
    private Optional<FakeSpoolApi> api = Optional.empty();

    public DeploySteps(CliUnderTest cli) {
        this.cli = cli;
    }

    @After
    public void stopTheApi() {
        api.ifPresent(FakeSpoolApi::stop);
    }

    @Given("a Spool API that accepts the secret {string} and has the live node {string}")
    public void aSpoolApi(String secret, String node) throws IOException {
        FakeSpoolApi started = new FakeSpoolApi(secret, node);
        api = Optional.of(started);
        cli.placeholder(API_PLACEHOLDER, started.address().toString());
    }

    @Given("a Spool API that accepts the secret {string} and has the live node {string} with the labels {string}")
    public void aSpoolApiWithALabelledNode(String secret, String node, String labels) throws IOException {
        aSpoolApi(secret, node);
        api.orElseThrow().labelTheLiveNode(Set.of(labels.split(",")));
    }

    @Then("the API received the descriptor {string} as {string}, asking for the labels {string}")
    public void theApiReceivedTheDescriptorAskingFor(String file, String name, String labels) throws IOException {
        JsonNode pipeline = deployed(name);
        assertThat(pipeline.get("main").asText()).isEqualTo(file);
        List<String> asked = new ArrayList<>();
        pipeline.path("labels").forEach(label -> asked.add(label.asText()));
        assertThat(asked).containsExactly(labels.split(","));
    }

    @Given("the environment variable {string} is the address of the API")
    public void theVariableIsTheApi(String name) {
        cli.setEnvironment(name, api.orElseThrow().address().toString());
    }

    @Given("the API has no live node")
    public void noLiveNode() {
        api.orElseThrow().noLiveNode();
    }

    @Given("the API has the deployment {string} running on {string} and {string} waiting for a node")
    public void theApiHas(String running, String node, String waiting) {
        api.orElseThrow().has(running, Optional.of(node), "RUNNING");
        api.orElseThrow().has(waiting, Optional.empty(), "WAITING");
    }

    @Given("the environment has neither {string} nor {string}")
    public void theEnvironmentHasNeither(String first, String second) {
        cli.removeEnvironment(first);
        cli.removeEnvironment(second);
    }

    @Then("the API received the descriptor {string} as {string}, signed with the secret")
    public void theApiReceivedTheDescriptor(String file, String name) throws IOException {
        JsonNode pipeline = deployed(name);
        assertThat(pipeline.get("main").asText()).isEqualTo(file);
        assertThat(pipeline.get("files").get(file).asText()).isEqualTo(Files.readString(cli.folder().resolve(file)));
        assertThat(api.orElseThrow().received()).singleElement().satisfies(request -> assertThat(request.signed()).isTrue());
    }

    @Then("the API received {string} with the main file {string} and the files {string} and {string}")
    public void theApiReceivedTheFiles(String name, String main, String first, String second) throws IOException {
        JsonNode pipeline = deployed(name);
        assertThat(pipeline.get("main").asText()).isEqualTo(main);
        List<String> files = new ArrayList<>();
        pipeline.get("files").fieldNames().forEachRemaining(files::add);
        assertThat(files).containsExactlyInAnyOrder(main, first, second);
        for (String file : files) {
            assertThat(pipeline.get("files").get(file).asText()).isEqualTo(Files.readString(cli.folder().resolve(file)));
        }
    }

    private JsonNode deployed(String name) throws IOException {
        FakeSpoolApi.Received request = api.orElseThrow().received().stream()
                .filter(received -> received.method().equals("PUT"))
                .filter(received -> received.path().equals("/spool/deployments/" + name))
                .findFirst().orElseThrow(() -> new AssertionError("the API received no deployment of " + name));
        return JSON.readTree(request.body());
    }

    @Given("I ran {string}")
    public void iRan(String commandLine) {
        cli.run(commandLine, "");
        assertThat(cli.exitCode()).as(cli.error()).isZero();
    }

    @Then("the API has the system {string} with the shared file {string}")
    public void theApiHasTheSystem(String system, String shared) throws IOException {
        assertThat(api.orElseThrow().sharedFilesOf(system))
                .containsEntry(shared, Files.readString(cli.folder().resolve(shared)));
    }

    @Then("the API has the pipeline {string} of {string} with the main file {string}")
    public void theApiHasThePipeline(String pipeline, String system, String main) {
        assertThat(api.orElseThrow().pipelineOf(system, pipeline)).isPresent();
        FakeSpoolApi.Received put = api.orElseThrow().received().stream()
                .filter(received -> received.path().equals("/spool/systems/" + system + "/pipelines/" + pipeline))
                .findFirst().orElseThrow();
        assertThat(put.body()).contains("\"main\":\"" + main + "\"");
    }

    @Then("the API received nothing")
    public void theApiReceivedNothing() {
        assertThat(api.orElseThrow().received()).isEmpty();
    }
}
