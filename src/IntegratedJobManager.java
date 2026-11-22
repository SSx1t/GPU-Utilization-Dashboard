import java.util.ArrayList;

// Connects Person 2's JobManager to Person 3's Scheduler.
//
//   submitJob()      JobManager checks the inputs and reserves the minutes, then the job
//                    joins the scheduler's FCFS queue (it starts at once if a GPU is free).
//   cancelJob()      JobManager checks the permission and updates the allowance, then the
//                    scheduler removes the job from the queue, or frees its GPU and starts
//                    the next waiting job.
//   onJobCompleted() The scheduler finished a job, so its reservation is released and its
//                    runtime is added to the used minutes (exactly once).
//
// Because this class extends JobManager, StudentDashboardPanel can use it without changes:
// the overridden methods run instead of the JobManager versions (polymorphism).
public class IntegratedJobManager extends JobManager implements SchedulerListener {

    public static final int MAX_TITLE_LENGTH = 60;
    public static final int MAX_APPLICATION_LENGTH = 60;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_REASON_LENGTH = 200;

    private Scheduler scheduler;
    private SystemChangeListener changeListener;   // the GpuSystem, which saves every change

    public IntegratedJobManager(Scheduler scheduler) {
        super();
        this.scheduler = scheduler;
        scheduler.setListener(this);
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setChangeListener(SystemChangeListener changeListener) {
        this.changeListener = changeListener;
    }

    // Accepts a job, reserves its full duration and adds it to the FCFS queue.
    // The submission time always comes from the simulation clock, so the value passed in is ignored.
    @Override
    public Job submitJob(String studentId, String title, String applicationName,
            String description, String taskSize, String submissionTime) {

        SimulationClock clock = scheduler.getClock();

        if (!clock.isInitialised()) {
            setLastMessage("The simulation clock has not been started yet.\n"
                    + "The administrator must set the start date and time first.");
            return null;
        }

        String problem = checkJobText(title, applicationName, description);

        if (problem != null) {
            setLastMessage(problem);
            return null;
        }

        Job job = super.submitJob(studentId, title.trim(), applicationName.trim(),
                description.trim(), taskSize, clock.formattedNow());

        if (job == null) {
            return null;    // JobManager has already set the message (e.g. not enough minutes)
        }

        scheduler.submit(job);
        setLastMessage(describeSubmission(job));
        notifyChange("Job J" + job.getJobId() + " submitted");

        return job;
    }

    // Cancels a queued or running job. JobManager checks the permission and the reason and
    // updates the allowance; then the scheduler removes the job from the queue or frees its GPU.
    // The elapsed runtime is read from the GPU itself, so the value passed in is not used.
    @Override
    public boolean cancelJob(int jobId, String cancelledBy, String reason,
            String cancellationTime, int elapsedRuntime) {

        Job job = findJob(jobId);

        if (job == null) {
            setLastMessage("Job not found.");
            return false;
        }

        if (cancelledBy == null || cancelledBy.trim().isEmpty()) {
            setLastMessage("The user who cancels the job is unknown.");
            return false;
        }

        if (reason != null && reason.trim().length() > MAX_REASON_LENGTH) {
            setLastMessage("The reason can have at most " + MAX_REASON_LENGTH + " characters.");
            return false;
        }

        String user = cancelledBy.trim();
        String reasonText = (reason == null) ? null : reason.trim();
        String statusBefore = job.getStatus();
        int runtime = 0;

        if (statusBefore.equals(Scheduler.RUNNING)) {
            runtime = scheduler.getGpu(job.getAssignedGpuId()).getMinutesRun();
        }

        boolean cancelled = super.cancelJob(jobId, user, reasonText,
                scheduler.getClock().formattedNow(), runtime);

        if (!cancelled) {
            return false;   // JobManager has already set the message
        }

        if (statusBefore.equals(Scheduler.QUEUED)) {
            scheduler.cancelQueued(job, user, reasonText);
            setLastMessage("Queued job J" + jobId + " was cancelled.\n"
                    + "Nothing was charged and its " + job.getEstimatedDuration()
                    + " reserved minutes were released.");
        } else {
            scheduler.cancelRunning(job, user, reasonText);
            setLastMessage("Running job J" + jobId + " was cancelled after " + runtime + " minute(s).\n"
                    + "Charged: " + runtime + " minute(s). The rest of the reservation was released"
                    + " and GPU" + job.getAssignedGpuId() + " was given to the next waiting job.");
        }

        notifyChange("Job J" + jobId + " cancelled");
        return true;
    }

    // Jobs are completed only by the scheduler when the clock advances.
    @Override
    public boolean completeJob(int jobId, String finishTime) {
        setLastMessage("Jobs are completed by the scheduler when the clock advances.");
        return false;
    }

    // ----- SchedulerListener: the scheduler reports what happened to a job -----

    @Override
    public void onJobCompleted(Job job, int runtimeMinutes) {
        settle(job, runtimeMinutes);
    }

    @Override
    public void onRunningCancelled(Job job, int runtimeMinutes) {
        // Normally cancelJob() has already settled the job; this covers direct scheduler calls.
        settle(job, runtimeMinutes);
    }

    @Override
    public void onQueuedCancelled(Job job) {
        if (job.isSettled()) {
            return;
        }

        TimeAllowance allowance = findAllowance(job.getStudentId());

        if (allowance != null && allowance.releaseReservation(job.getEstimatedDuration())) {
            job.settleJob(0);
        }
    }

    // Releases the job's reservation and charges its runtime. A job is settled only once.
    private void settle(Job job, int runtimeMinutes) {
        if (job.isSettled()) {
            return;
        }

        TimeAllowance allowance = findAllowance(job.getStudentId());

        if (allowance != null && allowance.settleTime(job.getEstimatedDuration(), runtimeMinutes)) {
            job.settleJob(runtimeMinutes);
        }
    }

    // ----- helpers -----

    // Returns a message describing the first problem, or null when the text is valid.
    private String checkJobText(String title, String applicationName, String description) {
        if (title == null || title.trim().isEmpty()) {
            return "The job title is required.";
        }

        if (applicationName == null || applicationName.trim().isEmpty()) {
            return "The application name is required.";
        }

        if (description == null || description.trim().isEmpty()) {
            return "The job description is required.";
        }

        if (title.trim().length() > MAX_TITLE_LENGTH) {
            return "The job title can have at most " + MAX_TITLE_LENGTH + " characters.";
        }

        if (applicationName.trim().length() > MAX_APPLICATION_LENGTH) {
            return "The application name can have at most " + MAX_APPLICATION_LENGTH + " characters.";
        }

        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            return "The description can have at most " + MAX_DESCRIPTION_LENGTH + " characters.";
        }

        return null;
    }

    // Builds the confirmation message: where the job runs, or its place in the queue.
    private String describeSubmission(Job job) {
        TimeAllowance allowance = findAllowance(job.getStudentId());

        String text = "Job J" + job.getJobId() + " was submitted successfully.\n"
                + job.getEstimatedDuration() + " minutes were reserved; "
                + allowance.getAvailableMinutes() + " minutes are still available.\n\n";

        if (job.getStatus().equals(Scheduler.RUNNING)) {
            GPU gpu = scheduler.getGpu(job.getAssignedGpuId());

            return text + "It started at once on GPU" + gpu.getId() + ".\n"
                    + "Expected finish: " + SimulationClock.format(gpu.expectedFinish());
        }

        ArrayList<QueuePrediction> predictions = scheduler.predictQueue();

        for (int i = 0; i < predictions.size(); i++) {
            QueuePrediction prediction = predictions.get(i);

            if (prediction.getJob() == job) {
                return text + "All GPUs are busy, so it is waiting in the queue.\n"
                        + "Queue position: " + prediction.getPosition() + "\n"
                        + "Predicted start: " + SimulationClock.format(prediction.getPredictedStart()) + "\n"
                        + "Predicted finish: " + SimulationClock.format(prediction.getPredictedFinish()) + "\n"
                        + "Waiting time: " + prediction.getRemainingWaitMinutes() + " minutes";
            }
        }

        return text + "It is waiting in the queue.";
    }

    private void notifyChange(String change) {
        if (changeListener != null) {
            changeListener.systemChanged(change);
        }
    }
}
