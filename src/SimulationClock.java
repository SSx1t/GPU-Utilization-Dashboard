import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// The simulation clock. Set once, then it only moves forward one minute at a time.
public class SimulationClock {

    public static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private LocalDateTime start;
    private LocalDateTime now;
    private int elapsedMinutes = 0;
    private boolean initialised = false;

    public void initialise(LocalDateTime startTime) {
        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null.");
        }
        start = startTime;
        now = startTime;
        elapsedMinutes = 0;
        initialised = true;
    }

    // Used by the GUI: read the start time typed as "dd/MM/yyyy HH:mm".
    public void initialiseFromText(String text) {
        initialise(LocalDateTime.parse(text.trim(), FMT));
    }

    public void advance() {
        requireInitialised();
        now = now.plusMinutes(1);
        elapsedMinutes++;
    }

    public boolean isInitialised() {
        return initialised;
    }

    public LocalDateTime now() {
        requireInitialised();
        return now;
    }

    public LocalDateTime getStart() {
        requireInitialised();
        return start;
    }

    public int getElapsedMinutes() {
        return elapsedMinutes;
    }

    public String formattedNow() {
        return initialised ? FMT.format(now) : "--/--/---- --:--";
    }

    public static String format(LocalDateTime t) {
        return (t == null) ? "-" : FMT.format(t);
    }

    // For Person 4 to restore the clock after reload.
    public void restore(LocalDateTime start, LocalDateTime now, int elapsedMinutes) {
        this.start = start;
        this.now = now;
        this.elapsedMinutes = elapsedMinutes;
        this.initialised = true;
    }

    private void requireInitialised() {
        if (!initialised) {
            throw new IllegalStateException("Set the start time before advancing.");
        }
    }
}
