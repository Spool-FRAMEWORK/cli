package software.spool.cli.api;

import java.util.List;
import java.util.Optional;

/**
 * A deployment as the Spool API describes it: its name, its version, the labels a node needs to run it (ADR-036), its
 * state, its node and its error, if any.
 */
public record DeploymentView(String name, int version, List<String> labels, String state, Optional<String> node,
                             Optional<String> error) {
    static final String WAITING = "WAITING";

    public boolean waits() {
        return WAITING.equals(state);
    }
}
