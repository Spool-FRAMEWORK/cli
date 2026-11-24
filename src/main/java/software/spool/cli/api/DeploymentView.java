package software.spool.cli.api;

import java.util.Optional;

/** A deployment as the Spool API describes it: its name, its version, its state, its node and its error, if any. */
public record DeploymentView(String name, int version, String state, Optional<String> node, Optional<String> error) {
    static final String WAITING = "WAITING";

    public boolean waits() {
        return WAITING.equals(state);
    }
}
