import java.util.ArrayList;

public class JobManager {

    private ArrayList<Job> jobs;
    private ArrayList<TimeAllowance> allowances;

    private int nextJobId;
    private String lastMessage;

    public JobManager() {

        jobs = new ArrayList<Job>();
        allowances = new ArrayList<TimeAllowance>();

        nextJobId = 1;
        lastMessage = "";
    }

    public ArrayList<Job> getJobs() {
        return jobs;
    }

    public ArrayList<TimeAllowance> getAllowances() {
        return allowances;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    // Lets a subclass (IntegratedJobManager) report its own message.
    protected void setLastMessage(String message) {
        lastMessage = message;
    }

    public boolean addStudentAllowance(TimeAllowance allowance) {

        if (allowance == null) {
            return false;
        }

        if (findAllowance(allowance.getStudentId()) != null) {
            lastMessage = "The student already has an allowance.";
            return false;
        }

        allowances.add(allowance);
        lastMessage = "Student allowance added.";

        return true;
    }

    public TimeAllowance findAllowance(String studentId) {

        for (TimeAllowance allowance : allowances) {

            if (allowance.getStudentId().equals(studentId)) {
                return allowance;
            }
        }

        return null;
    }

    public Job findJob(int jobId) {

        for (Job job : jobs) {

            if (job.getJobId() == jobId) {
                return job;
            }
        }

        return null;
    }

    public Job submitJob(String studentId, String title, String applicationName, String description, String taskSize, String submissionTime) {

        TimeAllowance allowance = findAllowance(studentId);

        if (allowance == null) {
            lastMessage = "No allowance was found for this student.";
            return null;
        }

        if (title == null || title.trim().equals("")) {
            lastMessage = "The job title is required.";
            return null;
        }

        if (applicationName == null || applicationName.trim().equals("")) {
            lastMessage = "The application name is required.";
            return null;
        }

        if (description == null || description.trim().equals("")) {
            lastMessage = "The job description is required.";
            return null;
        }

        Job job = new Job(nextJobId, studentId, title, applicationName, description, taskSize, submissionTime
        );

        if (job.getEstimatedDuration() == 0) {
            lastMessage = "Invalid task size.";
            return null;
        }

        if (!allowance.reserveMinutes(job.getEstimatedDuration())) {
            lastMessage = "The student does not have enough available minutes.";
            return null;
        }

        jobs.add(job);
        nextJobId++;

        lastMessage = "Job submitted successfully.";

        return job;
    }

    public ArrayList<Job> getStudentJobs(String studentId) {

        ArrayList<Job> studentJobs = new ArrayList<Job>();

        for (Job job : jobs) {

            if (job.getStudentId().equals(studentId)) {
                studentJobs.add(job);
            }
        }

        return studentJobs;
    }

    public ArrayList<Job> searchStudentJobs(String studentId, String keyword) {

        ArrayList<Job> results = new ArrayList<Job>();

        if (keyword == null) {
            return results;
        }

        String searchWord = keyword.toLowerCase();

        for (Job job : jobs) {

            boolean belongsToStudent =
                    job.getStudentId().equals(studentId);

            boolean matchesSearch =
                    job.getTitle().toLowerCase().contains(searchWord)
                    || job.getApplicationName().toLowerCase().contains(searchWord)
                    || job.getStatus().toLowerCase().contains(searchWord)
                    || ("" + job.getJobId()).contains(searchWord);

            if (belongsToStudent && matchesSearch) {
                results.add(job);
            }
        }

        return results;
    }

    public boolean cancelJob(int jobId, String cancelledBy, String reason, String cancellationTime, int elapsedRuntime) {

        Job job = findJob(jobId);

        if (job == null) {
            lastMessage = "Job not found.";
            return false;
        }

        boolean isAdministrator =
                cancelledBy.equalsIgnoreCase("admin");

        boolean isOwner =
                job.getStudentId().equals(cancelledBy);

        if (!isAdministrator && !isOwner) {
            lastMessage = "You do not have permission to cancel this job.";
            return false;
        }

        if (reason == null || reason.trim().equals("")) {
            lastMessage = "A cancellation reason is required.";
            return false;
        }

        if (job.getStatus().equals("Completed")
                || job.getStatus().equals("Cancelled")) {

            lastMessage = "This job cannot be cancelled.";
            return false;
        }

        TimeAllowance allowance =
                findAllowance(job.getStudentId());

        if (allowance == null) {
            lastMessage = "The student's allowance was not found.";
            return false;
        }

        if (job.getStatus().equals("Queued")) {

            boolean released = allowance.releaseReservation(
                    job.getEstimatedDuration()
            );

            if (!released) {
                lastMessage = "The reservation could not be released.";
                return false;
            }

            job.cancelJob(cancellationTime, cancelledBy, reason);

            job.settleJob(0);

            lastMessage =
                    "Queued job cancelled with no charge.";

            return true;
        }

        if (job.getStatus().equals("Running")) {

            if (elapsedRuntime < 0
                    || elapsedRuntime > job.getEstimatedDuration()) {

                lastMessage = "Invalid elapsed runtime.";
                return false;
            }

            boolean settled = allowance.settleTime(
                    job.getEstimatedDuration(),
                    elapsedRuntime
            );

            if (!settled) {
                lastMessage =
                        "The running job could not be settled.";

                return false;
            }

            job.cancelJob(cancellationTime, cancelledBy, reason);

            job.settleJob(elapsedRuntime);

            lastMessage = "Running job cancelled. Charged minutes: " + elapsedRuntime;

            return true;
        }

        return false;
    }

    public boolean completeJob(int jobId, String finishTime) {

        Job job = findJob(jobId);

        if (job == null) {
            lastMessage = "Job not found.";
            return false;
        }

        if (!job.getStatus().equals("Running")) {
            lastMessage = "Only a running job can be completed.";
            return false;
        }

        if (job.isSettled()) {
            lastMessage = "This job has already been settled.";
            return false;
        }

        TimeAllowance allowance = findAllowance(job.getStudentId());

        if (allowance == null) {
            lastMessage = "The student's allowance was not found.";
            return false;
        }

        boolean timeSettled = allowance.settleTime(job.getEstimatedDuration(),job.getEstimatedDuration()
        );

        if (!timeSettled) {
            lastMessage = "The allowance could not be updated.";
            return false;
        }

        job.completeJob(finishTime);
        job.settleJob(job.getEstimatedDuration());

        lastMessage = "Job completed successfully.";

        return true;
    }

    public boolean grantExtraTime(String studentId, int minutes, String reason, String time) {

        TimeAllowance allowance = findAllowance(studentId);

        if (allowance == null) {
            lastMessage = "Student allowance not found.";
            return false;
        }

        boolean granted = allowance.grantExtraTime(minutes, reason, time);

        if (granted) {
            lastMessage = "Extra time granted successfully.";
            return true;
        }

        lastMessage = "Extra time could not be granted.";
        return false;
    }

    public void addLoadedJob(Job job) {

        if (job != null) {
            jobs.add(job);

            if (job.getJobId() >= nextJobId) {
                nextJobId = job.getJobId() + 1;
            }
        }
    }
}