import java.time.LocalDateTime;

// One GPU. It holds one job at a time and counts the minutes it has run.
public class GPU {

    private int id;              // 1..4
    private Job currentJob;      // null when free
    private int minutesRun;
    private LocalDateTime startedAt;   // when the current job started (for display)

    public GPU(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public boolean isFree() {
        return currentJob == null;
    }

    public boolean isBusy() {
        return currentJob != null;
    }

    public Job getCurrentJob() {
        return currentJob;
    }

    public int getMinutesRun() {
        return minutesRun;
    }

    public void start(Job job, LocalDateTime startedAt) {
        if (isBusy()) {
            throw new IllegalStateException("GPU " + id + " is already busy.");
        }
        currentJob = job;
        minutesRun = 0;
        this.startedAt = startedAt;
    }

    // Called once per Advance Minute for the running job.
    public void tick() {
        if (isBusy()) {
            minutesRun++;
        }
    }

    public int remainingRuntime() {
        if (isFree()) return 0;
        int left = currentJob.getEstimatedDuration() - minutesRun;
        return (left > 0) ? left : 0;
    }

    public boolean isFinished() {
        return isBusy() && minutesRun >= currentJob.getEstimatedDuration();
    }

    public LocalDateTime expectedFinish() {
        if (isFree() || startedAt == null) return null;
        return startedAt.plusMinutes(currentJob.getEstimatedDuration());
    }

    // Take the job off the GPU (the Scheduler updates the job's status).
    public Job release() {
        Job j = currentJob;
        currentJob = null;
        minutesRun = 0;
        startedAt = null;
        return j;
    }

    // Re-attach a running job after reload, keeping its elapsed minutes.
    public void restore(Job job, int minutesRun, LocalDateTime startedAt) {
        this.currentJob = job;
        this.minutesRun = minutesRun;
        this.startedAt = startedAt;
    }

    @Override
    public String toString() {
        return "GPU" + id;
    }
}
