package app.kinderjournal.db;

/**
 * Raised when a database operation fails.
 *
 * <p>Checked {@code SQLException}s are wrapped in this unchecked type so the DAO
 * and UI code stays readable; the UI catches it in one place and shows the
 * teacher a clear message rather than losing the failure silently.
 */
public class DataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What the application was trying to do, in plain language. */
    private final String action;

    public DataException(String action, Throwable cause) {
        super(action + ": " + (cause == null ? "unknown error" : cause.getMessage()), cause);
        this.action = action;
    }

    public DataException(String message) {
        super(message);
        this.action = message;
    }

    /** The plain-language description of the failed action. */
    public String getAction() {
        return action;
    }
}
