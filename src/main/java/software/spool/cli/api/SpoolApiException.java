package software.spool.cli.api;

/** What went wrong talking to the Spool API, in words for whoever runs the CLI. */
public final class SpoolApiException extends RuntimeException {
    public SpoolApiException(String message) {
        super(message);
    }

    public SpoolApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
