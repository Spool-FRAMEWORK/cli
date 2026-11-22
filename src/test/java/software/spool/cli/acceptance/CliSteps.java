package software.spool.cli.acceptance;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.opentest4j.TestAbortedException;
import software.spool.cli.program.PathPrograms;
import software.spool.cli.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CliSteps {
    private static final String POM_WITHOUT_DEPENDENCIES = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>fixture</groupId>
                <artifactId>project</artifactId>
                <version>1</version>
            </project>
            """;
    private final CliUnderTest cli;

    public CliSteps(CliUnderTest cli) {
        this.cli = cli;
    }

    @Given("the descriptor {string}:")
    public void theDescriptor(String name, String content) throws IOException {
        Files.writeString(cli.folder().resolve(name), content);
    }

    @Given("the descriptor {string} uses the aggregator {string} of the project")
    public void theDescriptorUsesTheAggregator(String name, String aggregator) throws IOException {
        theDescriptor(name, ProjectFixture.descriptorUsing(aggregator));
    }

    @Given("the project classes are compiled into {string}")
    public void theProjectClassesAreCompiledInto(String folder) throws IOException {
        ProjectFixture.compileRunningTotalInto(cli.folder().resolve(folder));
    }

    @Given("a Maven project with the aggregator {string} and the descriptor {string} that uses it")
    public void aMavenProject(String aggregator, String name) throws IOException {
        if (new PathPrograms(System::getenv, Terminal.ofTheConsole()).find("mvn").isEmpty())
            throw new TestAbortedException("Maven is not installed: put mvn on the path");
        Files.writeString(cli.folder().resolve("pom.xml"), POM_WITHOUT_DEPENDENCIES);
        theDescriptorUsesTheAggregator(name, aggregator);
    }

    @Given("the project is compiled")
    public void theProjectIsCompiled() throws IOException {
        ProjectFixture.compileRunningTotalInto(cli.folder().resolve("target/classes"));
    }

    @Given("Terraform is installed")
    public void terraformIsInstalled() {
        cli.terraformInstalled(true);
    }

    @Given("Terraform is not installed")
    public void terraformIsNotInstalled() {
        cli.terraformInstalled(false);
    }

    @When("the environment has {word} set to {string}")
    public void theEnvironmentHas(String name, String value) {
        cli.setEnvironment(name, value);
    }

    @When("I run {string}")
    @When("I run {string} in the project")
    public void iRun(String commandLine) {
        cli.run(commandLine, "");
    }

    @When("I run {string} and answer {string}")
    public void iRunAndAnswer(String commandLine, String answer) {
        cli.run(commandLine, answer + System.lineSeparator());
    }

    @Then("the exit code is {int}")
    public void theExitCodeIs(int exitCode) {
        assertThat(cli.exitCode()).as("exit code, with error: %s", cli.error()).isEqualTo(exitCode);
    }

    @Then("the output says {string}")
    public void theOutputSays(String text) {
        assertThat(cli.output()).contains(text);
    }

    @Then("the error says {string}")
    public void theErrorSays(String text) {
        assertThat(cli.error()).contains(text);
    }

    @Then("the folder {string} has the file {string}")
    public void theFolderHasTheFile(String folder, String file) {
        assertThat(cli.folder().resolve(folder).resolve(file)).isRegularFile();
    }

    @Then("Terraform was not run with {string}")
    public void terraformWasNotRunWith(String argument) {
        assertThat(cli.terraformRuns()).noneMatch(run -> run.arguments().contains(argument));
    }

    @Then("Terraform was run with {string}")
    public void terraformWasRunWith(String argument) {
        assertThat(cli.terraformRuns()).anyMatch(run -> run.arguments().contains(argument));
    }

    @Then("Terraform was run with {string} and then with {string} in the folder of the generated Terraform")
    public void terraformWasRunInOrder(String first, String second) {
        List<CliUnderTest.Run> runs = cli.terraformRuns();
        assertThat(runs).extracting(run -> run.arguments().get(0)).containsExactly(first, second);
        assertThat(runs).allSatisfy(run -> assertThat(run.folder().resolve("main.tf.json")).isRegularFile());
    }
}
