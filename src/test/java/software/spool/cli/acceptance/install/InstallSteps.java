package software.spool.cli.acceptance.install;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class InstallSteps {
    private final InstallUnderTest install;
    private Optional<String> userPathOfWindowsBefore = Optional.empty();

    public InstallSteps(InstallUnderTest install) {
        this.install = install;
    }

    @Given("a release with spool.zip and its SHA-256")
    public void aRelease() throws Exception {
        install.release(true);
    }

    @Given("a release whose spool.zip does not match its SHA-256")
    public void aReleaseWithAWrongSha256() throws Exception {
        install.release(false);
    }

    @Given("SPOOL_NO_MODIFY_PATH is set")
    public void noModifyPath() {
        install.set("SPOOL_NO_MODIFY_PATH", "1");
    }

    @Given("SPOOL_INSTALL_JRE is {string}")
    public void installJre(String answer) {
        install.set("SPOOL_INSTALL_JRE", answer);
    }

    @Given("there is no Java on the PATH")
    public void noJava() {
        install.withoutJava();
    }

    @When("{word} runs")
    public void runs(String fileName) throws Exception {
        InstallScript script = InstallScript.named(fileName);
        if (script == InstallScript.PS1) userPathOfWindowsBefore = Optional.of(install.userPathOfWindows());
        install.run(script);
    }

    @Then("it ends with exit code {int}")
    public void itEndsWith(int exitCode) {
        assertThat(install.exitCode()).as("exit code, with output: %s", install.output()).isEqualTo(exitCode);
    }

    @Then("spool --version answers from the bin folder of the installation")
    public void spoolAnswers() throws Exception {
        assertThat(install.launcherVersion()).startsWith("spool ");
    }

    @Then("the profile of the user adds the bin folder of the installation to the PATH")
    public void theProfileAddsIt() throws Exception {
        assertThat(pathLinesOfTheProfile()).isEqualTo(1);
    }

    @Then("running install.sh again does not add it twice")
    public void runningAgain() throws Exception {
        install.run(InstallScript.SH);
        assertThat(pathLinesOfTheProfile()).isEqualTo(1);
    }

    @Then("the PATH of the user is not changed")
    public void thePathIsNotChanged() throws Exception {
        if (install.script() == InstallScript.SH) assertThat(install.home().resolve(".profile")).doesNotExist();
        else assertThat(install.userPathOfWindows()).isEqualTo(userPathOfWindowsBefore.orElseThrow());
    }

    @Then("the script says to add the bin folder of the installation to the PATH")
    public void theScriptSaysWhatToAdd() {
        assertThat(separatorFree(install.output())).contains(separatorFree("Add " + install.installation().resolve("bin")));
    }

    @Then("it ends with an error that says {string}")
    public void itEndsWithAnError(String text) {
        assertThat(install.exitCode()).isNotZero();
        assertThat(install.output()).contains(text);
    }

    @Then("nothing is installed")
    public void nothingIsInstalled() {
        assertThat(install.installation().resolve("lib")).doesNotExist();
    }

    @Then("the script says {string}")
    public void theScriptSays(String text) {
        assertThat(install.output()).contains(text);
    }

    @Then("it offers to download a Temurin JRE into the installation")
    public void itOffersAJre() {
        assertThat(separatorFree(install.output())).contains("Temurin").contains(separatorFree(install.installation().resolve("jre").toString()));
    }

    @Then("spool is installed all the same")
    public void spoolIsInstalled() {
        assertThat(install.installation().resolve("lib")).isDirectory();
    }

    private static String separatorFree(String text) {
        return text.replace(File.separatorChar, '/');
    }

    private long pathLinesOfTheProfile() throws Exception {
        Path profile = install.home().resolve(".profile");
        return Files.readAllLines(profile).stream().filter(line -> line.startsWith("export PATH=") && line.contains(".spool")).count();
    }
}
