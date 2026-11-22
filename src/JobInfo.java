import java.time.LocalDateTime;

// A read-only summary of one job for the tables and detail dialogs: queue position,
// predicted or actual start and finish, waiting time and runtime.
// GpuSystem creates these objects from the job record, the scheduler's predictions
// and the simulation clock; nothing here changes the job.
public class JobInfo {

    private Job job;
    private int queuePosition;          // 1, 2, 3 ... while queued, otherwise 0
    private LocalDateTime start;        // predicted start (queued) or actual start
    private LocalDateTime finish;       // predicted, expected or actual finish
    private boolean predicted;          // true when start and finish are predictions
    private int remainingWait;          // queued jobs: predicted start - current time
    private int queueWait;              // minutes spent waiting (so far, for queued jobs)
    private int runtime;                // minutes executed on a GPU (so far, for running jobs)
    private int remainingRuntime;       // running jobs only

    public JobInfo(Job job, int queuePosition, LocalDateTime start, LocalDateTime finish,
            boolean predicted, int remainingWait, int queueWait, int runtime, int remainingRuntime) {

        this.job = job;
        this.queuePosition = queuePosition;
        this.start = start;
        this.finish = finish;
        this.predicted = predicted;
        this.remainingWait = remainingWait;
        this.queueWait = queueWait;
        this.runtime = runtime;
        this.remainingRuntime = remainingRuntime;
    }

    public Job getJob() {
        return job;
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getFinish() {
        return finish;
    }

    public boolean isPredicted() {
        return predicted;
    }

    public int getRemainingWait() {
        return remainingWait;
    }

    public int getQueueWait() {
        return queueWait;
    }

    public int getRuntime() {
        return runtime;
    }

    public int getRemainingRuntime() {
        return remainingRuntime;
    }

    public boolean isQueued() {
        return job.getStatus().equals(Scheduler.QUEUED);
    }

    public boolean isRunning() {
        return job.getStatus().equals(Scheduler.RUNNING);
    }

    // True for a cancelled job that never reached a GPU.
    public boolean isCancelledInQueue() {
        return job.getStatus().equals(Scheduler.CANCELLED) && job.getStartTime() == null;
    }

    // ----- text for the tables -----

    public String getJobLabel() {
        return "J" + job.getJobId();
    }

    public String getGpuText() {
        if (job.getAssignedGpuId() == -1) {
            return "-";
        }

        return "GPU" + job.getAssignedGpuId();
    }

    public String getPositionText() {
        if (queuePosition > 0) {
            return Integer.toString(queuePosition);
        }

        return "-";
    }

    public String getDurationText() {
        return job.getEstimatedDuration() + " min";
    }

    public String getStartText() {
        return SimulationClock.format(start);
    }

    public String getFinishText() {
        return SimulationClock.format(finish);
    }

    public String getRemainingWaitText() {
        if (isQueued()) {
            return remainingWait + " min";
        }

        return "-";
    }

    public String getQueueWaitText() {
        return queueWait + " min";
    }

    public String getRuntimeText() {
        if (isQueued() || isCancelledInQueue()) {
            return "-";
        }

        return runtime + " min";
    }

    public String getChargedText() {
        if (job.isSettled()) {
            return job.getChargedMinutes() + " min";
        }

        return "-";
    }

    // Every detail of the job on separate lines, for the "View details" dialogs.
    public String describe() {
        String text = "Job " + getJobLabel() + ": " + job.getTitle()
                + "\nStudent: " + job.getStudentId()
                + "\nApplication: " + job.getApplicationName()
                + "\nDescription: " + job.getDescription()
                + "\nTask size: " + job.getTaskSize() + " (" + job.getWorkloadUnits()
                + " workload units, " + job.getEstimatedDuration() + " minutes)"
                + "\nStatus: " + job.getStatus()
                + "\nSubmitted: " + job.getSubmissionTime();

        if (isQueued()) {
            text += "\n\nQueue position: " + getPositionText()
                    + "\nPredicted start: " + getStartText()
                    + "\nPredicted finish: " + getFinishText()
                    + "\nRemaining waiting time: " + remainingWait + " min"
                    + "\nWaited so far: " + queueWait + " min"
                    + "\nGPU: assigned when the job starts";
        } else if (isRunning()) {
            text += "\n\nGPU: " + getGpuText()
                    + "\nStarted: " + getStartText()
                    + "\nExpected finish: " + getFinishText()
                    + "\nRuntime so far: " + runtime + " min (" + remainingRuntime + " min remaining)"
                    + "\nTime waited in the queue: " + queueWait + " min";
        } else if (isCancelledInQueue()) {
            text += "\n\nCancelled while waiting in the queue (it never used a GPU)."
                    + "\nTime waited before cancellation: " + queueWait + " min";
        } else {
            text += "\n\nGPU: " + getGpuText()
                    + "\nStarted: " + getStartText()
                    + "\nFinished: " + getFinishText()
                    + "\nRuntime: " + runtime + " min"
                    + "\nTime waited in the queue: " + queueWait + " min";
        }

        if (job.isSettled()) {
            text += "\nAllowance charged: " + job.getChargedMinutes() + " min";
        } else {
            text += "\nAllowance reserved: " + job.getEstimatedDuration()
                    + " min (settled when the job ends)";
        }

        if (job.getStatus().equals(Scheduler.CANCELLED)) {
            text += "\n\nCancelled at: " + job.getCancellationTime()
                    + "\nCancelled by: " + job.getCancelledBy()
                    + "\nReason: " + job.getCancellationReason();
        }

        return text;
    }
}
