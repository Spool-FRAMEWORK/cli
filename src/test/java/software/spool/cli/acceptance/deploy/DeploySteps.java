package software.spool.cli.acceptance.deploy;

import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import software.spool.cli.acceptance.CliUnderTest;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class DeploySteps {
    private static final String API_PLACEHOLDER = "{api}";

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
        String descriptor = Files.readString(cli.folder().resolve(file));
        assertThat(api.orElseThrow().received()).singleElement().satisfies(request -> {
            assertThat(request.method()).isEqualTo("PUT");
            assertThat(request.path()).isEqualTo("/spool/deployments/" + name);
            assertThat(request.body()).isEqualTo(descriptor);
            assertThat(request.signed()).isTrue();
        });
    }

    @Then("the API received nothing")
    public void theApiReceivedNothing() {
        assertThat(api.orElseThrow().received()).isEmpty();
    }
}
