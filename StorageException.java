// Thrown when the simulation data file cannot be read or written.
// The message always says which file (and, when loading, which line) caused the problem.
public class StorageException extends Exception {
    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }
}
