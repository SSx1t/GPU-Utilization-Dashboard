import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

// Brings everyone's parts together. The screens talk to this class instead of changing
// the data directly, which keeps the interface code separate from the domain logic.
//
//   Person 1  AccountManager        accounts, registration requests and login
//   Person 2  JobManager            jobs and allowances (used here as IntegratedJobManager)
//   Person 3  Scheduler             GPUs, FCFS queue, predictions and the simulation clock
//   Person 4  StorageManager        saving and restoring, search and dashboard summaries
//
// Every change is saved straight away, so nothing is lost when the program is closed.
// The simulation clock only moves when Advance Minute is pressed, so it pauses while closed.
public class GpuSystem implements SystemChangeListener {

    public static final String ACCOUNTS_FILE = "accounts.txt";
    public static final String SIMULATION_FILE = "simulation.txt";

    public static final int MAX_GRANT_MINUTES = 10000;     // largest single grant
    public static final int MAX_ALLOCATION = 1000000;      // largest total allocation
    public static final int MAX_REASON_LENGTH = 200;

    private File folder;
    private AccountManager accounts;
    private SimulationClock clock;
    private Scheduler scheduler;
    private IntegratedJobManager jobs;
    private StorageManager storage;

    private ArrayList<SystemChangeListener> listeners = new ArrayList<SystemChangeListener>();
    private String saveError;     // message of the last failed automatic save, or null

    // Opens (or creates) the data folder and restores everything that was saved there.
    public GpuSystem(String folderName) throws IOException, AccountException, StorageException {
        folder = new File(folderName);

        if (!folder.isDirectory() && !folder.mkdirs()) {
            throw new IOException("Cannot create the data folder " + folder.getAbsolutePath());
        }

        accounts = new AccountManager(getAccountsFile().getPath());
        clock = new SimulationClock();
        scheduler = new Scheduler(clock);
        jobs = new IntegratedJobManager(scheduler);
        storage = new StorageManager(getSimulationFile().getPath());

        storage.load(clock, scheduler, jobs);
        checkStudentsExist();
        syncAllowances();

        jobs.setChangeListener(this);
    }

    // ----- the parts -----

    public AccountManager getAccountManager() {
        return accounts;
    }

    public IntegratedJobManager getJobManager() {
        return jobs;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public SimulationClock getClock() {
        return clock;
    }

    public File getAccountsFile() {
        return new File(folder, ACCOUNTS_FILE);
    }

    public File getSimulationFile() {
        return new File(folder, SIMULATION_FILE);
    }

    public String getDataFolder() {
        return folder.getPath();
    }

    // ----- saving and change notifications -----

    public void addChangeListener(SystemChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(SystemChangeListener listener) {
        listeners.remove(listener);
    }

    // Called after every change: saves the data, then lets the screens refresh.
    @Override
    public void systemChanged(String change) {
        try {
            save();
        } catch (StorageException e) {
            saveError = e.getMessage();
        }

        // Copy the list in case a listener removes itself while being notified.
        ArrayList<SystemChangeListener> copy = new ArrayList<SystemChangeListener>(listeners);

        for (int i = 0; i < copy.size(); i++) {
            copy.get(i).systemChanged(change);
        }
    }

    // Writes simulation.txt (accounts.txt is written by AccountManager itself).
    public void save() throws StorageException {
        storage.save(clock, scheduler, jobs);
        saveError = null;
    }

    // Returns the message of the last failed automatic save and forgets it, or null.
    public String takeSaveError() {
        String message = saveError;
        saveError = null;
        return message;
    }

    // ----- accounts and allowances -----

    // Called after the administrator approves or rejects a registration request.
    public void accountsChanged() {
        syncAllowances();
        systemChanged("Accounts changed");
    }

    // Every approved student gets exactly one allowance in the job manager, and the
    // Student object refers to that same allowance.
    public void syncAllowances() {
        ArrayList<Student> students = accounts.getStudents();

        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            TimeAllowance allowance = jobs.findAllowance(student.getUsername());

            if (allowance == null) {
                jobs.addStudentAllowance(student.getTimeAllowance());
            } else {
                student.setTimeAllowance(allowance);
            }
        }
    }

    // The two files must agree: every allowance belongs to an approved account.
    private void checkStudentsExist() throws StorageException {
        ArrayList<TimeAllowance> allowances = jobs.getAllowances();

        for (int i = 0; i < allowances.size(); i++) {
            String id = allowances.get(i).getStudentId();
            Student student = accounts.findStudent(id);

            if (student == null || !student.getUsername().equals(id)) {
                throw new StorageException(getSimulationFile().getPath() + " has data for student " + id
                        + ", who is not an approved account in " + getAccountsFile().getPath() + ".");
            }
        }
    }

    public ArrayList<Student> getStudents() {
        return accounts.getStudents();
    }

    public TimeAllowance getAllowance(String studentId) {
        return jobs.findAllowance(studentId);
    }

    public int countRequests(String status) {
        ArrayList<AccessRequest> requests = accounts.getRequests();
        int count = 0;

        for (int i = 0; i < requests.size(); i++) {
            if (requests.get(i).getStatus().equals(status)) {
                count++;
            }
        }

        return count;
    }

    // ----- administrator actions -----

    // Starts the simulation clock. This is allowed once; afterwards only Advance Minute moves it.
    public void initialiseClock(LocalDateTime start) throws AccountException {
        requireAdmin();

        if (start == null) {
            throw new IllegalArgumentException("Choose a start date and time.");
        }

        if (clock.isInitialised()) {
            throw new IllegalStateException("The clock has already been started. From now on use Advance Minute.");
        }

        clock.initialise(start.withSecond(0).withNano(0));
        systemChanged("Clock started at " + clock.formattedNow());
    }

    // Moves the clock one minute: running jobs progress, finished jobs complete and are
    // settled, and waiting jobs start on the free GPUs.
    public void advanceMinute() throws AccountException {
        requireAdmin();

        if (!clock.isInitialised()) {
            throw new IllegalStateException("Set the start date and time before advancing the clock.");
        }

        scheduler.advanceMinute();
        systemChanged("Clock advanced to " + clock.formattedNow());
    }

    // Cancels any queued or running job. Returns the message describing the result.
    public String cancelJobAsAdmin(int jobId, String reason) throws AccountException {
        requireAdmin();

        String admin = accounts.getCurrentUser().getUsername();

        if (!jobs.cancelJob(jobId, admin, reason, clock.formattedNow(), 0)) {
            throw new IllegalArgumentException(jobs.getLastMessage());
        }

        return jobs.getLastMessage();
    }

    // Adds extra minutes to a student's allocation and records the reason.
    public String grantExtraTime(String studentId, int minutes, String reason) throws AccountException {
        requireAdmin();

        TimeAllowance allowance = findAllowanceOrFail(studentId);
        String reasonText = checkReason(reason);

        if (minutes <= 0 || minutes > MAX_GRANT_MINUTES) {
            throw new IllegalArgumentException("Extra minutes must be a whole number from 1 to " + MAX_GRANT_MINUTES + ".");
        }

        if ((long) allowance.getAllocatedMinutes() + minutes > MAX_ALLOCATION) {
            throw new IllegalArgumentException("The total allocation cannot be more than " + MAX_ALLOCATION + " minutes.");
        }

        if (!jobs.grantExtraTime(allowance.getStudentId(), minutes, reasonText, recordTime())) {
            throw new IllegalArgumentException(jobs.getLastMessage());
        }

        systemChanged("Extra time granted to " + allowance.getStudentId());

        return "Granted " + minutes + " extra minutes to " + allowance.getStudentId()
                + ".\nAvailable now: " + allowance.getAvailableMinutes() + " minutes.";
    }

    // Sets a new total allocation. It cannot drop below the used plus reserved minutes,
    // so a balance can never become negative.
    public String changeAllocation(String studentId, int newAllocation, String reason) throws AccountException {
        requireAdmin();

        TimeAllowance allowance = findAllowanceOrFail(studentId);
        String reasonText = checkReason(reason);
        int oldAllocation = allowance.getAllocatedMinutes();
        int committed = allowance.getUsedMinutes() + allowance.getReservedMinutes();

        if (newAllocation < 0 || newAllocation > MAX_ALLOCATION) {
            throw new IllegalArgumentException("The allocation must be a whole number from 0 to " + MAX_ALLOCATION + ".");
        }

        if (newAllocation == oldAllocation) {
            throw new IllegalArgumentException("The allocation is already " + oldAllocation + " minutes.");
        }

        if (!allowance.adjustAllocatedMinutes(newAllocation)) {
            throw new IllegalArgumentException("The allocation cannot be less than the used plus reserved minutes ("
                    + committed + ").");
        }

        allowance.getGrantHistory().add(recordTime() + " | Student ID: " + allowance.getStudentId()
                + " | Allocation changed from " + oldAllocation + " to " + newAllocation
                + " minutes | Reason: " + reasonText);

        systemChanged("Allocation changed for " + allowance.getStudentId());

        return "The allocation of " + allowance.getStudentId() + " is now " + newAllocation
                + " minutes.\nAvailable now: " + allowance.getAvailableMinutes() + " minutes.";
    }

    private void requireAdmin() throws AccountException {
        if (!(accounts.getCurrentUser() instanceof Administrator)) {
            throw new AccountException("Administrator login required.");
        }
    }

    private TimeAllowance findAllowanceOrFail(String studentId) {
        TimeAllowance allowance = (studentId == null) ? null : jobs.findAllowance(studentId.trim());

        if (allowance == null) {
            throw new IllegalArgumentException("Select an approved student.");
        }

        return allowance;
    }

    private String checkReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("A reason is required.");
        }

        if (reason.trim().length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reason can have at most " + MAX_REASON_LENGTH + " characters.");
        }

        return reason.trim();
    }

    // Time written into allowance records.
    private String recordTime() {
        if (clock.isInitialised()) {
            return clock.formattedNow();
        }

        return "Before clock start";
    }

    // ----- searching -----

    // All jobs that match the keyword (empty = every job) and the status ("All" = every status).
    // The keyword matches the job ID ("12" or "J12"), the student ID, title, application,
    // description, task size or status, ignoring upper and lower case.
    public ArrayList<Job> searchJobs(String keyword, String status) {
        return filterJobs(jobs.getJobs(), keyword, status);
    }

    // The same search, limited to one student's own jobs.
    public ArrayList<Job> searchStudentJobs(String studentId, String keyword, String status) {
        return filterJobs(jobs.getStudentJobs(studentId), keyword, status);
    }

    // Approved students whose university ID contains the text (empty = every student).
    public ArrayList<Student> searchStudents(String idText) {
        String text = (idText == null) ? "" : idText.trim().toLowerCase();
        ArrayList<Student> students = accounts.getStudents();
        ArrayList<Student> result = new ArrayList<Student>();

        for (int i = 0; i < students.size(); i++) {
            if (students.get(i).getUsername().toLowerCase().contains(text)) {
                result.add(students.get(i));
            }
        }

        return result;
    }

    private ArrayList<Job> filterJobs(ArrayList<Job> list, String keyword, String status) {
        String word = (keyword == null) ? "" : keyword.trim().toLowerCase();
        ArrayList<Job> result = new ArrayList<Job>();

        for (int i = 0; i < list.size(); i++) {
            Job job = list.get(i);
            boolean statusMatches = status == null || status.equals("All") || job.getStatus().equalsIgnoreCase(status);

            if (statusMatches && matches(job, word)) {
                result.add(job);
            }
        }

        return result;
    }

    private boolean matches(Job job, String word) {
        if (word.isEmpty()) {
            return true;
        }

        // "12" or "j12" finds job 12 exactly.
        String number = word.startsWith("j") ? word.substring(1) : word;

        if (isWholeNumber(number) && Integer.parseInt(number) == job.getJobId()) {
            return true;
        }

        return job.getStudentId().toLowerCase().contains(word)
                || job.getTitle().toLowerCase().contains(word)
                || job.getApplicationName().toLowerCase().contains(word)
                || job.getDescription().toLowerCase().contains(word)
                || job.getTaskSize().toLowerCase().contains(word)
                || job.getStatus().toLowerCase().contains(word);
    }

    private boolean isWholeNumber(String text) {
        if (text.isEmpty() || text.length() > 9) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    // ----- dashboard summaries -----

    public ArrayList<Job> getAllJobs() {
        return new ArrayList<Job>(jobs.getJobs());
    }

    public ArrayList<Job> getStudentJobs(String studentId) {
        return jobs.getStudentJobs(studentId);
    }

    public Job findJob(int jobId) {
        return jobs.findJob(jobId);
    }

    public int countJobs(String status) {
        ArrayList<Job> list = jobs.getJobs();
        int count = 0;

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getStatus().equals(status)) {
                count++;
            }
        }

        return count;
    }

    public int getBusyGpuCount() {
        return scheduler.busyGpuCount();
    }

    public String getUtilisationText() {
        return scheduler.utilisationText();
    }

    // The waiting jobs in queue order, followed by the running jobs (GPU1 to GPU4).
    public ArrayList<JobInfo> getActiveJobs() {
        ArrayList<Job> list = scheduler.getQueue();
        GPU[] gpus = scheduler.getGpus();

        for (int i = 0; i < gpus.length; i++) {
            if (gpus[i].isBusy()) {
                list.add(gpus[i].getCurrentJob());
            }
        }

        return describeJobs(list);
    }

    // Completed jobs, the most recently finished first.
    public ArrayList<JobInfo> getCompletedJobs() {
        ArrayList<JobInfo> result = describeJobs(searchJobs("", Scheduler.COMPLETED));

        Collections.sort(result, new Comparator<JobInfo>() {
            @Override
            public int compare(JobInfo a, JobInfo b) {
                int byFinish = b.getFinish().compareTo(a.getFinish());

                if (byFinish != 0) {
                    return byFinish;
                }

                return b.getJob().getJobId() - a.getJob().getJobId();
            }
        });

        return result;
    }

    // Queue position, times, waits and runtime of each job in the list.
    public ArrayList<JobInfo> describeJobs(ArrayList<Job> list) {
        HashMap<Integer, QueuePrediction> predictions = predictionsById();
        ArrayList<JobInfo> result = new ArrayList<JobInfo>();

        for (int i = 0; i < list.size(); i++) {
            result.add(describe(list.get(i), predictions));
        }

        return result;
    }

    public JobInfo describeJob(Job job) {
        return describe(job, predictionsById());
    }

    // The scheduler's predictions, looked up by job ID.
    private HashMap<Integer, QueuePrediction> predictionsById() {
        HashMap<Integer, QueuePrediction> map = new HashMap<Integer, QueuePrediction>();
        ArrayList<QueuePrediction> predictions = scheduler.predictQueue();

        for (int i = 0; i < predictions.size(); i++) {
            map.put(predictions.get(i).getJob().getJobId(), predictions.get(i));
        }

        return map;
    }

    // Works the values out from the job record, the predictions and the clock.
    private JobInfo describe(Job job, HashMap<Integer, QueuePrediction> predictions) {
        String status = job.getStatus();
        LocalDateTime submitted = parse(job.getSubmissionTime());
        LocalDateTime start = parse(job.getStartTime());
        LocalDateTime finish = parse(job.getFinishTime());
        LocalDateTime now = clock.isInitialised() ? clock.now() : null;

        if (status.equals(Scheduler.QUEUED)) {
            QueuePrediction prediction = predictions.get(job.getJobId());
            int waitedSoFar = minutesBetween(submitted, now);

            if (prediction == null) {
                return new JobInfo(job, 0, null, null, true, 0, waitedSoFar, 0, 0);
            }

            return new JobInfo(job, prediction.getPosition(), prediction.getPredictedStart(),
                    prediction.getPredictedFinish(), true, prediction.getRemainingWaitMinutes(),
                    waitedSoFar, 0, 0);
        }

        if (status.equals(Scheduler.RUNNING)) {
            GPU gpu = scheduler.getGpu(job.getAssignedGpuId());

            return new JobInfo(job, 0, start, gpu.expectedFinish(), false, 0,
                    minutesBetween(submitted, start), gpu.getMinutesRun(), gpu.remainingRuntime());
        }

        if (status.equals(Scheduler.CANCELLED) && start == null) {
            // Cancelled while waiting: it waited until the cancellation and never ran.
            LocalDateTime cancelled = parse(job.getCancellationTime());
            return new JobInfo(job, 0, null, null, false, 0, minutesBetween(submitted, cancelled), 0, 0);
        }

        // Completed, or cancelled while running (its finish time is the cancellation time).
        return new JobInfo(job, 0, start, finish, false, 0,
                minutesBetween(submitted, start), minutesBetween(start, finish), 0);
    }

    private LocalDateTime parse(String time) {
        if (time == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(time, SimulationClock.FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private int minutesBetween(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            return 0;
        }

        long minutes = ChronoUnit.MINUTES.between(from, to);

        return (minutes < 0) ? 0 : (int) minutes;
    }
}
