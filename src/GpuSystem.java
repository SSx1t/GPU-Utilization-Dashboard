import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

// Connects the parts of the project and saves them (Person 4). No interface code here.
// It extends JobManager, so Person 2's StudentDashboardPanel uses it unchanged, while every
// accepted job also joins Person 3's Scheduler queue (inheritance and polymorphism).
// As the SchedulerListener it settles allowances when jobs complete or are cancelled.
// AccountManager saves accounts.txt; this class saves everything else in simulation.txt.
public class GpuSystem extends JobManager implements SchedulerListener {

    public static final String ACCOUNTS_FILE = "accounts.txt";
    public static final String SIMULATION_FILE = "simulation.txt";

    private File folder;
    private AccountManager accounts;
    private SimulationClock clock = new SimulationClock();
    private Scheduler scheduler = new Scheduler(clock);
    private String message;     // replaces JobManager's message after the actions below
    private String saveError;   // the last automatic save problem, or null

    // Loads both files from the folder. The first time there are no files and nothing is loaded.
    public GpuSystem(String folderName) throws IOException {
        folder = new File(folderName);
        folder.mkdirs();
        File accountsFile = new File(folder, ACCOUNTS_FILE);

        try {
            accounts = new AccountManager(accountsFile.getPath());
        } catch (Exception e) {
            throw new IOException(accountsFile.getPath() + ": " + e.getMessage());
        }

        scheduler.setListener(this);
        load();
    }

    public AccountManager getAccountManager() {
        return accounts;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public SimulationClock getClock() {
        return clock;
    }

    public File getFolder() {
        return folder;
    }

    public String getSaveError() {
        return saveError;
    }

    @Override
    public String getLastMessage() {
        return (message != null) ? message : super.getLastMessage();
    }

    // ----- jobs: JobManager's rules, then the scheduler -----

    @Override
    public Job submitJob(String studentId, String title, String application, String description,
            String taskSize, String submissionTime) {
        message = null;

        if (!clock.isInitialised()) {
            message = "The administrator has not started the simulation clock yet.";
            return null;
        }

        Job job = super.submitJob(studentId, title, application, description, taskSize, clock.formattedNow());

        if (job != null) {
            scheduler.submit(job);
            message = "Job J" + job.getJobId() + " was submitted. " + job.getEstimatedDuration()
                    + " minutes were reserved; " + findAllowance(studentId).getAvailableMinutes()
                    + " minutes are still available.\n\n";

            QueuePrediction place = findPrediction(scheduler.predictQueue(), job);

            if (place == null) {
                message += "It started at once on GPU" + job.getAssignedGpuId() + ".";
            } else {
                message += "All GPUs are busy, so it is waiting in the queue.\nQueue position: " + place.getPosition()
                        + "\nPredicted start: " + SimulationClock.format(place.getPredictedStart())
                        + "\nPredicted finish: " + SimulationClock.format(place.getPredictedFinish())
                        + "\nWaiting time: " + place.getRemainingWaitMinutes() + " minutes";
            }
            changed();
        }
        return job;
    }

    // Used by students (Person 2's panel) and by the administrator ("admin").
    // The time and runtime are taken from the clock and the GPU, not from the caller.
    @Override
    public boolean cancelJob(int jobId, String cancelledBy, String reason, String time, int elapsed) {
        message = null;
        Job job = findJob(jobId);
        boolean running = job != null && job.getStatus().equals(Scheduler.RUNNING);
        int runtime = running ? scheduler.getGpu(job.getAssignedGpuId()).getMinutesRun() : 0;

        if (!super.cancelJob(jobId, cancelledBy, reason, clock.formattedNow(), runtime)) {
            return false;     // JobManager's message says why
        }

        if (running) {
            scheduler.cancelRunning(job, cancelledBy, reason);   // frees the GPU, starts the next job
            message = "Running job J" + jobId + " was cancelled after " + runtime + " minute(s).\n"
                    + runtime + " minute(s) were charged, the rest of the reservation was released and GPU"
                    + job.getAssignedGpuId() + " was given to the next waiting job.";
        } else {
            scheduler.cancelQueued(job, cancelledBy, reason);
            message = "Queued job J" + jobId + " was cancelled. Nothing was charged.";
        }
        changed();
        return true;
    }

    // SchedulerListener: charge the runtime and release the reservation, once per job.
    @Override
    public void onJobCompleted(Job job, int runtimeMinutes) {
        settle(job, runtimeMinutes);
    }

    @Override
    public void onRunningCancelled(Job job, int runtimeMinutes) {
        settle(job, runtimeMinutes);
    }

    @Override
    public void onQueuedCancelled(Job job) {
        settle(job, 0);
    }

    private void settle(Job job, int runtime) {
        TimeAllowance allowance = findAllowance(job.getStudentId());

        if (!job.isSettled() && allowance != null && allowance.settleTime(job.getEstimatedDuration(), runtime)) {
            job.settleJob(runtime);
        }
    }

    // ----- administrator actions -----

    public void startClock(LocalDateTime start) {
        requireAdmin();

        if (clock.isInitialised()) {
            throw new IllegalStateException("The clock has already been started. Use +1 minute.");
        }
        clock.initialise(start);
        changed();
    }

    public void advanceMinute() {
        requireAdmin();
        scheduler.advanceMinute();
        changed();
    }

    @Override
    public boolean grantExtraTime(String studentId, int minutes, String reason, String time) {
        if (!checkChange(studentId, minutes, 1, reason)) {
            return false;
        }
        super.grantExtraTime(studentId, minutes, reason.trim(), recordTime());
        message = "Granted " + minutes + " extra minutes to " + studentId + ".\nAvailable now: "
                + findAllowance(studentId).getAvailableMinutes() + " minutes.";
        changed();
        return true;
    }

    public boolean changeAllocation(String studentId, int minutes, String reason) {
        if (!checkChange(studentId, minutes, 0, reason)) {
            return false;
        }
        TimeAllowance allowance = findAllowance(studentId);
        int old = allowance.getAllocatedMinutes();

        if (!allowance.adjustAllocatedMinutes(minutes)) {
            message = "The allocation cannot be less than the used plus reserved minutes ("
                    + (allowance.getUsedMinutes() + allowance.getReservedMinutes()) + ").";
            return false;
        }
        allowance.getGrantHistory().add(recordTime() + " | Student ID: " + studentId + " | Allocation changed from "
                + old + " to " + minutes + " minutes | Reason: " + reason.trim());
        message = "The allocation of " + studentId + " is now " + minutes + " minutes.\nAvailable now: "
                + allowance.getAvailableMinutes() + " minutes.";
        changed();
        return true;
    }

    private boolean checkChange(String studentId, int minutes, int smallest, String reason) {
        requireAdmin();

        if (findAllowance(studentId) == null) {
            message = "Select an approved student.";
        } else if (minutes < smallest) {
            message = "The minutes must be at least " + smallest + ".";
        } else if (reason == null || reason.trim().isEmpty()) {
            message = "A reason is required.";
        } else {
            return true;
        }
        return false;
    }

    private void requireAdmin() {
        if (!(accounts.getCurrentUser() instanceof Administrator)) {
            throw new IllegalStateException("Administrator login required.");
        }
    }

    private String recordTime() {
        return clock.isInitialised() ? clock.formattedNow() : "Before clock start";
    }

    // ----- students, search and predictions -----

    // Approved students as {id, name, email, phone, status, minutes}, read from AccountManager's
    // request summary (administrator only). A newly approved student gets an allowance here.
    public ArrayList<String[]> syncStudents() throws AccountException {
        ArrayList<String[]> students = requests("Approved");

        for (String[] student : students) {
            if (findAllowance(student[0]) == null) {
                addStudentAllowance(new TimeAllowance(student[0], number(student[5]), 0, 0));
                changed();
            }
        }
        return students;
    }

    public ArrayList<String[]> requests(String status) throws AccountException {
        ArrayList<String[]> list = new ArrayList<String[]>();

        for (String line : accounts.getRequestSummary().split("\n")) {
            String[] fields = line.split(" \\| ");

            if (fields.length == 6 && fields[4].equals(status)) {
                fields[5] = fields[5].replace("Initial minutes: ", "");
                list.add(fields);
            }
        }
        return list;
    }

    // Keyword (job number such as 12 or J12, student, title, application, description, size)
    // and status ("All" for every status).
    public static boolean matches(Job job, String keyword, String status) {
        String word = keyword.trim().toLowerCase();

        if (!status.equals("All") && !job.getStatus().equals(status)) {
            return false;
        }
        String text = job.getStudentId() + " " + job.getTitle() + " " + job.getApplicationName() + " "
                + job.getDescription() + " " + job.getTaskSize();

        return word.isEmpty() || word.equals("j" + job.getJobId()) || word.equals("" + job.getJobId())
                || text.toLowerCase().contains(word);
    }

    // Finds a waiting job's queue position, predicted start/finish and wait in the scheduler's
    // list of predictions. Returns null if the job is not waiting.
    public static QueuePrediction findPrediction(ArrayList<QueuePrediction> predictions, Job job) {
        for (QueuePrediction prediction : predictions) {
            if (prediction.getJob().getJobId() == job.getJobId()) {
                return prediction;
            }
        }
        return null;
    }

    // Minutes between two times written as dd/MM/yyyy HH:mm, or -1 if one is missing.
    public static int minutesBetween(String from, String to) {
        try {
            return (int) ChronoUnit.MINUTES.between(time(from), time(to));
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ----- storage (simulation.txt) -----
    // One record per line, fields separated by |. In text, \ | and line breaks are written as
    // \\ \p \n so any title or reason can be stored. An empty field means "none".
    //   CLOCK|start|now|elapsed minutes|busy GPU minutes
    //   ALLOWANCE|student|allocated|used|reserved
    //   HISTORY|student|record
    //   JOB|id|student|title|application|description|size|submitted|status|gpu|start|finish|
    //       cancelled at|cancelled by|reason|charged

    // Saves after every change. A problem is kept in saveError and shown by the window.
    private void changed() {
        try {
            save();
        } catch (IOException e) {
            saveError = e.getMessage();
        }
    }

    public void save() throws IOException {
        StringBuilder out = new StringBuilder();

        if (clock.isInitialised()) {
            line(out, "CLOCK", SimulationClock.format(clock.getStart()), clock.formattedNow(),
                    clock.getElapsedMinutes(), scheduler.getTotalBusyGpuMinutes());
        }
        for (TimeAllowance a : getAllowances()) {
            line(out, "ALLOWANCE", a.getStudentId(), a.getAllocatedMinutes(), a.getUsedMinutes(), a.getReservedMinutes());

            for (String record : a.getGrantHistory()) {
                line(out, "HISTORY", a.getStudentId(), record);
            }
        }
        for (Job j : getJobs()) {
            line(out, "JOB", j.getJobId(), j.getStudentId(), j.getTitle(), j.getApplicationName(), j.getDescription(),
                    j.getTaskSize(), j.getSubmissionTime(), j.getStatus(), (j.getAssignedGpuId() > 0) ? j.getAssignedGpuId() : null,
                    j.getStartTime(), j.getFinishTime(), j.getCancellationTime(), j.getCancelledBy(),
                    j.getCancellationReason(), j.getChargedMinutes());
        }

        // Write a temporary file first, so a failed save never leaves half a file.
        File file = new File(folder, SIMULATION_FILE);
        File temp = new File(folder, SIMULATION_FILE + ".tmp");
        Files.write(temp.toPath(), out.toString().getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        saveError = null;
    }

    private static void line(StringBuilder out, Object... fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append('|');
            }
            if (fields[i] != null) {
                out.append(fields[i].toString().replace("\\", "\\\\").replace("|", "\\p")
                        .replace("\n", "\\n").replace("\r", "\\r"));
            }
        }
        out.append('\n');
    }

    // Restores the saved state. A problem is reported with the file, line and reason.
    private void load() throws IOException {
        File file = new File(folder, SIMULATION_FILE);

        if (!file.exists()) {
            return;
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);

        for (int i = 0; i < lines.size(); i++) {
            try {
                if (!lines.get(i).trim().isEmpty()) {
                    restore(lines.get(i).split("\\|", -1));
                }
            } catch (RuntimeException e) {
                throw new IOException(file.getPath() + ", line " + (i + 1) + ": " + e.getMessage());
            }
        }
    }

    private void restore(String[] f) {
        if (f[0].equals("CLOCK") && f.length == 5) {
            clock.restore(time(f[1]), time(f[2]), number(f[3]));
            scheduler.restoreBusyMinutes(number(f[4]));

        } else if (f[0].equals("ALLOWANCE") && f.length == 5) {
            TimeAllowance allowance = new TimeAllowance(text(f[1]), number(f[2]), number(f[3]), number(f[4]));

            if (allowance.getAvailableMinutes() < 0 || !addStudentAllowance(allowance)) {
                throw new IllegalArgumentException("invalid or repeated allowance for " + f[1]);
            }

        } else if (f[0].equals("HISTORY") && f.length == 3) {
            allowanceOf(text(f[1])).getGrantHistory().add(text(f[2]));

        } else if (f[0].equals("JOB") && f.length == 16) {
            restoreJob(f);

        } else {
            throw new IllegalArgumentException("unknown record or wrong number of fields");
        }
    }

    // Rebuilds a job by repeating its state changes, then puts it back in the queue or on its GPU.
    private void restoreJob(String[] f) {
        int id = number(f[1]);
        String status = f[8];
        int gpu = f[9].isEmpty() ? -1 : number(f[9]);
        allowanceOf(text(f[2]));

        if (findJob(id) != null) {
            throw new IllegalArgumentException("job J" + id + " appears twice");
        }
        for (int i = 10; i <= 12; i++) {
            if (!f[i].isEmpty()) {
                time(f[i]);
            }
        }
        time(f[7]);
        Job job = new Job(id, text(f[2]), text(f[3]), text(f[4]), text(f[5]), text(f[6]), text(f[7]));

        if (job.getEstimatedDuration() == 0) {
            throw new IllegalArgumentException("unknown task size \"" + f[6] + "\"");
        }
        if (!f[10].isEmpty()) {
            if (gpu < 1 || gpu > Scheduler.GPU_COUNT) {
                throw new IllegalArgumentException("the GPU number must be 1 to " + Scheduler.GPU_COUNT);
            }
            job.startJob(gpu, text(f[10]));
        }
        if (status.equals(Scheduler.COMPLETED)) {
            job.completeJob(text(f[11]));
        } else if (status.equals(Scheduler.CANCELLED)) {
            job.cancelJob(text(f[12]), optional(f[13]), optional(f[14]));
        }
        if (!job.getStatus().equals(status)) {
            throw new IllegalArgumentException("job J" + id + " cannot be \"" + status + "\" with these times");
        }

        int minutesRun = 0;

        if (status.equals(Scheduler.COMPLETED) || status.equals(Scheduler.CANCELLED)) {
            job.settleJob(number(f[15]));

        } else if (status.equals(Scheduler.RUNNING)) {
            minutesRun = minutesBetween(job.getStartTime(), clock.formattedNow());

            if (scheduler.getGpu(gpu).isBusy() || minutesRun < 0 || minutesRun >= job.getEstimatedDuration()) {
                throw new IllegalArgumentException("running job J" + id + " does not match the clock or GPU" + gpu);
            }
        }
        addLoadedJob(job);
        scheduler.registerRestoredJob(job, minutesRun);
    }

    private TimeAllowance allowanceOf(String studentId) {
        TimeAllowance allowance = findAllowance(studentId);

        if (allowance == null) {
            throw new IllegalArgumentException("no allowance was saved for student " + studentId);
        }
        return allowance;
    }

    private static int number(String text) {
        try {
            if (Integer.parseInt(text.trim()) >= 0) {
                return Integer.parseInt(text.trim());
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new IllegalArgumentException("\"" + text + "\" must be a whole number (0 or more)");
    }

    private static LocalDateTime time(String text) {
        try {
            return LocalDateTime.parse(text, SimulationClock.FMT);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("\"" + text + "\" must be a date and time (dd/MM/yyyy HH:mm)");
        }
    }

    private static String optional(String field) {
        return field.isEmpty() ? null : text(field);
    }

    // Undoes the escaping done by line().
    private static String text(String field) {
        StringBuilder out = new StringBuilder();

        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);

            if (c == '\\' && i + 1 < field.length()) {
                char next = field.charAt(++i);
                out.append(next == 'p' ? '|' : next == 'n' ? '\n' : next == 'r' ? '\r' : next);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
