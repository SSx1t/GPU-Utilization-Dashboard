import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;

// Complete system tests for the integrated application (accounts + jobs + scheduler +
// storage + search). Each check() prints PASS or FAIL, like SchedulerTests.
// The tests use temporary folders, so the real "data" folder is never touched.
//
// Run in Eclipse: right-click SystemTests.java > Run As > Java Application.
// From a terminal: javac -d bin src/*.java  &&  java -cp bin SystemTests
public class SystemTests {

    private static int passed = 0;
    private static int failed = 0;
    private static ArrayList<File> folders = new ArrayList<File>();

    private static final Charset UTF8 = Charset.forName("UTF-8");

    public static void main(String[] args) throws Exception {
        try {
            section("Accounts and approval");
            testRegistrationAndApproval();

            section("Simulation clock");
            testClockRules();

            section("Allowance rules");
            testReservationAndLimits();
            testCompletionSettlesOnce();
            testQueuedCancellation();
            testRunningCancellation();
            testPermissionsAndStates();
            testExtraTimeAndAllocation();

            section("Scheduling and predictions");
            testFcfsAndGpuOrder();
            testExampleFromBrief();
            testWaitsRefresh();
            testUtilisation();

            section("Storage and restart");
            testRoundTrip();
            testContinuityAfterRestart();
            testSpecialCharacters();
            testMissingAndDamagedFiles();
            testRandomRestarts();

            section("Search");
            testSearch();

            section("Test scenario");
            testScenario();

        } finally {
            for (int i = 0; i < folders.size(); i++) {
                deleteFolder(folders.get(i));
            }
        }

        System.out.println("------------------------------------------");
        System.out.println("Passed: " + passed + "   Failed: " + failed);

        if (failed > 0) {
            System.exit(1);
        }
    }

    // =====================================================================
    // Accounts and approval
    // =====================================================================

    private static void testRegistrationAndApproval() throws Exception {
        GpuSystem system = new GpuSystem(newFolder());
        AccountManager accounts = system.getAccountManager();

        check("bad email is rejected", throwsAccountError(accounts, "S001", "Ali", "ali-at-example.com", "0501234567", "secret1"));
        check("short password is rejected", throwsAccountError(accounts, "S001", "Ali", "ali@example.com", "0501234567", "abc"));
        check("the ID admin is reserved", throwsAccountError(accounts, "admin", "Ali", "ali@example.com", "0501234567", "secret1"));

        accounts.register("S001", "Ali Hassan", "ali@example.com", "0501234567", "secret1");
        check("duplicate registration is rejected", throwsAccountError(accounts, "s001", "Ali", "ali2@example.com", "0501234567", "secret1"));

        check("a pending student cannot log in", !canLogin(accounts, "S001", "secret1"));
        check("wrong administrator password fails", !canLogin(accounts, "admin", "wrong"));

        accounts.register("S002", "Sara Ahmed", "sara@example.com", "0507654321", "secret2");
        accounts.register("S003", "Bad Request", "bad@example.com", "0507654321", "secret3");

        accounts.login("admin", "gpu2026");
        accounts.approve("S001");
        accounts.approve("S002", 60);
        accounts.reject("S003");
        system.accountsChanged();

        boolean repeated = false;

        try {
            accounts.approve("S001");
        } catch (AccountException e) {
            repeated = true;
        }

        check("repeated approval is blocked", repeated);
        check("approval gives 100 minutes by default", system.getAllowance("S001").getAllocatedMinutes() == 100);
        check("administrator can choose other minutes", system.getAllowance("S002").getAllocatedMinutes() == 60);
        check("a rejected request gets no allowance", system.getAllowance("S003") == null);
        check("2 pending/approved/rejected counts", system.countRequests("Approved") == 2 && system.countRequests("Rejected") == 1);

        accounts.logout();
        check("an approved student can log in", canLogin(accounts, "S001", "secret1"));
        check("a rejected student cannot log in", !canLogin(accounts, "S003", "secret3"));

        // The allowance object of the Student and of the job manager are the same object.
        Student student = accounts.findStudent("S001");
        check("student and job manager share one allowance", student.getTimeAllowance() == system.getAllowance("S001"));
    }

    // =====================================================================
    // Clock
    // =====================================================================

    private static void testClockRules() throws Exception {
        GpuSystem system = systemWithStudents(1, 100);
        IntegratedJobManager jobs = system.getJobManager();

        check("no submissions before the clock starts", jobs.submitJob("S001", "t", "a", "d", "Small", null) == null
                && jobs.getLastMessage().contains("not been started"));

        system.getAccountManager().logout();
        check("only the administrator can start the clock", throwsAccountException(system, LocalDateTime.of(2026, 11, 23, 9, 0)));

        system.getAccountManager().login("admin", "gpu2026");

        boolean advanceBeforeStart = false;

        try {
            system.advanceMinute();
        } catch (IllegalStateException e) {
            advanceBeforeStart = true;
        }

        check("Advance Minute needs a started clock", advanceBeforeStart);

        system.initialiseClock(LocalDateTime.of(2026, 11, 23, 9, 0, 45));
        check("the clock starts at the chosen minute", system.getClock().formattedNow().equals("23/11/2026 09:00"));

        boolean twice = false;

        try {
            system.initialiseClock(LocalDateTime.of(2026, 11, 24, 9, 0));
        } catch (IllegalStateException e) {
            twice = true;
        }

        check("the clock cannot be set a second time", twice);

        system.advanceMinute();
        check("Advance Minute moves one minute", system.getClock().formattedNow().equals("23/11/2026 09:01")
                && system.getClock().getElapsedMinutes() == 1);

        Job job = jobs.submitJob("S001", "t", "a", "d", "Small", "01/01/2000 00:00");
        check("submission time comes from the simulation clock", job != null && job.getSubmissionTime().equals("23/11/2026 09:01"));
    }

    // =====================================================================
    // Allowance rules
    // =====================================================================

    private static void testReservationAndLimits() throws Exception {
        GpuSystem system = startedSystem(1, 50);
        IntegratedJobManager jobs = system.getJobManager();
        TimeAllowance allowance = system.getAllowance("S001");

        Job large = jobs.submitJob("S001", "big", "app", "desc", "Large", null);
        check("Large job accepted with 50 available", large != null);
        check("the full duration is reserved", allowance.getReservedMinutes() == 30 && allowance.getAvailableMinutes() == 20);

        check("a job longer than the available time is rejected", jobs.submitJob("S001", "x", "app", "desc", "Large", null) == null);
        check("nothing is reserved for a rejected job", allowance.getReservedMinutes() == 30);

        Job medium = jobs.submitJob("S001", "m", "app", "desc", "Medium", null);
        Job small = jobs.submitJob("S001", "s", "app", "desc", "Small", null);
        check("an exact match is accepted (15 + 5 = 20 available)", medium != null && small != null);
        check("available time is now zero", allowance.getAvailableMinutes() == 0);
        check("zero available blocks new submissions", jobs.submitJob("S001", "z", "app", "desc", "Small", null) == null);

        advance(system, 30);
        check("reserved jobs still run to completion at zero balance",
                large.getStatus().equals(Scheduler.COMPLETED) && medium.getStatus().equals(Scheduler.COMPLETED)
                && small.getStatus().equals(Scheduler.COMPLETED));
        check("balance never negative and fully used", allowance.getUsedMinutes() == 50
                && allowance.getReservedMinutes() == 0 && allowance.getAvailableMinutes() == 0);

        check("blank title is rejected", jobs.submitJob("S001", "  ", "app", "desc", "Small", null) == null);
        check("invalid task size is rejected", jobs.submitJob("S001", "t", "app", "desc", "Huge", null) == null);
        check("too long title is rejected", jobs.submitJob("S001", repeat('x', 61), "app", "desc", "Small", null) == null);
    }

    private static void testCompletionSettlesOnce() throws Exception {
        GpuSystem system = startedSystem(1, 100);
        Job job = system.getJobManager().submitJob("S001", "t", "a", "d", "Medium", null);
        TimeAllowance allowance = system.getAllowance("S001");

        advance(system, 14);
        check("still running after 14 of 15 minutes", job.getStatus().equals(Scheduler.RUNNING) && allowance.getUsedMinutes() == 0);

        advance(system, 1);
        check("completed after 15 minutes", job.getStatus().equals(Scheduler.COMPLETED)
                && job.getFinishTime().equals("23/11/2026 09:15"));
        check("reservation released, full runtime used", allowance.getReservedMinutes() == 0 && allowance.getUsedMinutes() == 15);
        check("job charged and settled once", job.isSettled() && job.getChargedMinutes() == 15);

        advance(system, 5);
        check("no second charge later", allowance.getUsedMinutes() == 15);
        check("a completed job cannot be completed again by JobManager",
                !system.getJobManager().completeJob(job.getJobId(), "23/11/2026 09:20"));
    }

    private static void testQueuedCancellation() throws Exception {
        GpuSystem system = startedSystem(5, 100);
        IntegratedJobManager jobs = system.getJobManager();
        fillGpus(jobs, "Large");

        Job waiting = jobs.submitJob("S005", "t", "a", "d", "Medium", null);
        TimeAllowance allowance = system.getAllowance("S005");
        int availableBefore = allowance.getAvailableMinutes();

        advance(system, 3);
        boolean ok = jobs.cancelJob(waiting.getJobId(), "S005", "No longer needed", null, 0);

        check("student cancels own queued job", ok && waiting.getStatus().equals(Scheduler.CANCELLED));
        check("queued cancellation charges nothing", waiting.getChargedMinutes() == 0 && allowance.getUsedMinutes() == 0);
        check("its reservation is released", allowance.getAvailableMinutes() == availableBefore + 15);
        check("it leaves the queue and never gets a GPU", system.getScheduler().getQueue().isEmpty()
                && waiting.getAssignedGpuId() == -1 && waiting.getStartTime() == null);
        check("cancellation time, user and reason are recorded", "23/11/2026 09:03".equals(waiting.getCancellationTime())
                && "S005".equals(waiting.getCancelledBy()) && "No longer needed".equals(waiting.getCancellationReason()));
        check("running jobs are untouched", system.getBusyGpuCount() == 4);
    }

    private static void testRunningCancellation() throws Exception {
        GpuSystem system = startedSystem(5, 100);
        IntegratedJobManager jobs = system.getJobManager();
        Job[] running = fillGpus(jobs, "Large");
        Job waiting = jobs.submitJob("S005", "t", "a", "d", "Small", null);
        TimeAllowance owner = system.getAllowance("S002");

        advance(system, 7);
        String message = system.cancelJobAsAdmin(running[1].getJobId(), "GPU maintenance");

        check("administrator cancels a running job", running[1].getStatus().equals(Scheduler.CANCELLED)
                && "admin".equals(running[1].getCancelledBy()));
        check("elapsed runtime (7 min) is charged", running[1].getChargedMinutes() == 7 && owner.getUsedMinutes() == 7);
        check("the full reservation is released", owner.getReservedMinutes() == 0 && owner.getAvailableMinutes() == 93);
        check("finish time = cancellation time", "23/11/2026 09:07".equals(running[1].getFinishTime()));
        check("GPU2 is freed and the next job starts at once", system.getScheduler().getGpu(2).getCurrentJob() == waiting
                && "23/11/2026 09:07".equals(waiting.getStartTime()));
        check("the result message mentions the charge", message.contains("Charged: 7"));

        advance(system, 2);
        boolean ok = jobs.cancelJob(running[0].getJobId(), "S001", "Wrong parameters", null, 99);
        check("student cancels own running job; passed runtime is ignored",
                ok && running[0].getChargedMinutes() == 9 && system.getAllowance("S001").getUsedMinutes() == 9);
    }

    private static void testPermissionsAndStates() throws Exception {
        GpuSystem system = startedSystem(2, 100);
        IntegratedJobManager jobs = system.getJobManager();
        Job job = jobs.submitJob("S001", "t", "a", "d", "Small", null);

        check("a student cannot cancel another student's job", !jobs.cancelJob(job.getJobId(), "S002", "mine now", null, 0)
                && job.getStatus().equals(Scheduler.RUNNING));
        check("a cancellation needs a reason", !jobs.cancelJob(job.getJobId(), "S001", "   ", null, 0));

        advance(system, 5);
        check("a completed job cannot be cancelled", !jobs.cancelJob(job.getJobId(), "S001", "late", null, 0)
                && job.getStatus().equals(Scheduler.COMPLETED));

        Job second = jobs.submitJob("S001", "t", "a", "d", "Small", null);
        jobs.cancelJob(second.getJobId(), "S001", "stop", null, 0);
        check("a cancelled job cannot be cancelled again", !jobs.cancelJob(second.getJobId(), "admin", "again", null, 0));
        check("a cancelled job cannot restart", !second.startJob(1, "23/11/2026 09:06"));

        system.getAccountManager().logout();
        boolean blocked = false;

        try {
            system.cancelJobAsAdmin(second.getJobId(), "x");
        } catch (AccountException e) {
            blocked = true;
        }

        check("administrator actions need an administrator login", blocked);
    }

    private static void testExtraTimeAndAllocation() throws Exception {
        GpuSystem system = startedSystem(1, 100);
        TimeAllowance allowance = system.getAllowance("S001");

        String message = system.grantExtraTime("S001", 25, "Project deadline");
        check("extra time increases the allocation", allowance.getAllocatedMinutes() == 125
                && message.contains("Available now: 125 minutes"));
        check("the grant is recorded with time and reason", allowance.getGrantHistory().size() == 1
                && allowance.getGrantHistory().get(0).contains("Added 25 minutes")
                && allowance.getGrantHistory().get(0).contains("Project deadline")
                && allowance.getGrantHistory().get(0).startsWith("23/11/2026 09:00"));

        check("a grant needs a reason", throwsIllegalArgument(system, "S001", 10, " "));
        check("a grant needs positive minutes", throwsIllegalArgument(system, "S001", 0, "reason"));
        check("a grant needs an approved student", throwsIllegalArgument(system, "S999", 10, "reason"));

        system.getJobManager().submitJob("S001", "t", "a", "d", "Very large", null);
        boolean tooLow = false;

        try {
            system.changeAllocation("S001", 50, "cut");
        } catch (IllegalArgumentException e) {
            tooLow = true;
        }

        check("allocation cannot drop below used + reserved", tooLow && allowance.getAllocatedMinutes() == 125);

        system.changeAllocation("S001", 80, "Course policy");
        check("allocation change is applied and recorded", allowance.getAllocatedMinutes() == 80
                && allowance.getAvailableMinutes() == 20 && allowance.getGrantHistory().size() == 2);
    }

    // =====================================================================
    // Scheduling
    // =====================================================================

    private static void testFcfsAndGpuOrder() throws Exception {
        GpuSystem system = startedSystem(5, 100);
        IntegratedJobManager jobs = system.getJobManager();
        Job[] running = fillGpus(jobs, "Small");
        Job fifth = jobs.submitJob("S005", "t", "a", "d", "Small", null);

        boolean order = true;

        for (int i = 0; i < 4; i++) {
            if (system.getScheduler().getGpu(i + 1).getCurrentJob() != running[i]) {
                order = false;
            }
        }

        check("first four jobs run on GPU1 to GPU4 in order", order);
        check("the fifth job waits with no GPU and no times", fifth.getStatus().equals(Scheduler.QUEUED)
                && fifth.getAssignedGpuId() == -1 && fifth.getStartTime() == null && fifth.getFinishTime() == null);

        advance(system, 5);
        check("when GPUs free up, the waiting job takes the lowest GPU", system.getScheduler().getGpu(1).getCurrentJob() == fifth);
        check("GPU history is kept after completion", running[3].getAssignedGpuId() == 4
                && running[3].getStatus().equals(Scheduler.COMPLETED));
    }

    // The example dashboard in the project brief: at 10:20 all GPUs are busy until
    // 10:25, 10:30, 10:35 and 10:40; J8 (15 min) and J9 (5 min) wait.
    private static void testExampleFromBrief() throws Exception {
        GpuSystem system = exampleSystem();
        ArrayList<QueuePrediction> predictions = system.getScheduler().predictQueue();

        check("example: all four GPUs busy", system.getBusyGpuCount() == 4);
        check("example: two predictions", predictions.size() == 2);

        QueuePrediction first = predictions.get(0);
        QueuePrediction second = predictions.get(1);

        check("example: J8 position 1, start 10:25, wait 5", first.getJob().getJobId() == 8 && first.getPosition() == 1
                && SimulationClock.format(first.getPredictedStart()).equals("23/11/2026 10:25")
                && first.getRemainingWaitMinutes() == 5);
        check("example: J8 predicted finish 10:40", SimulationClock.format(first.getPredictedFinish()).equals("23/11/2026 10:40"));
        check("example: J9 position 2, start 10:30, wait 10", second.getJob().getJobId() == 9 && second.getPosition() == 2
                && SimulationClock.format(second.getPredictedStart()).equals("23/11/2026 10:30")
                && second.getRemainingWaitMinutes() == 10);

        Job[] before = currentGpuJobs(system);
        system.getScheduler().predictQueue();
        check("predictions do not change live GPU assignments", sameJobs(before, currentGpuJobs(system))
                && system.getScheduler().getQueue().size() == 2);

        JobInfo info = system.describeJob(system.findJob(8));
        check("JobInfo shows the same prediction", info.getPositionText().equals("1")
                && info.getRemainingWaitText().equals("5 min") && info.isPredicted());

        advance(system, 5);
        check("example: J8 starts on GPU1 at 10:25", "23/11/2026 10:25".equals(system.findJob(8).getStartTime())
                && system.findJob(8).getAssignedGpuId() == 1);
    }

    private static void testWaitsRefresh() throws Exception {
        GpuSystem system = exampleSystem();
        IntegratedJobManager jobs = system.getJobManager();

        advance(system, 2);
        check("wait drops by the minutes advanced", waitOf(system, 9) == 8);

        jobs.cancelJob(8, "S003", "not needed", null, 0);
        check("cancelling a job ahead moves J9 up and shortens its wait",
                waitOf(system, 9) == 3 && system.describeJob(system.findJob(9)).getQueuePosition() == 1);

        Job late = jobs.submitJob("S001", "t", "a", "d", "Small", null);
        check("a new submission joins the end of the queue", system.describeJob(late).getQueuePosition() == 2);
    }

    private static void testUtilisation() throws Exception {
        GpuSystem system = startedSystem(2, 100);
        IntegratedJobManager jobs = system.getJobManager();

        check("utilization is N/A at time zero", system.getUtilisationText().equals("N/A"));

        Job first = jobs.submitJob("S001", "t", "a", "d", "Medium", null);
        advance(system, 4);
        jobs.cancelJob(first.getJobId(), "S001", "stop", null, 0);
        advance(system, 6);

        // 4 busy GPU minutes before the cancellation, then nothing: 4 / (4 x 10) = 10%.
        check("time before cancellation counts, waiting does not", system.getScheduler().getTotalBusyGpuMinutes() == 4
                && system.getUtilisationText().equals("10%"));
    }

    // =====================================================================
    // Storage
    // =====================================================================

    private static void testRoundTrip() throws Exception {
        String folder = newFolder();
        GpuSystem original = scenario(folder);
        GpuSystem restored = new GpuSystem(folder);

        check("restart restores the same state (jobs, GPUs, queue, balances, clock)",
                snapshot(original).equals(snapshot(restored)));
        check("restart keeps remaining runtimes", restored.getScheduler().getGpu(3).remainingRuntime() == 25);
        check("restart keeps the queue order", queueIds(restored).equals("19,20,21,22"));
        check("restart keeps utilization", restored.getUtilisationText().equals(original.getUtilisationText()));
        check("restart keeps the grant history", restored.getAllowance("S002").getGrantHistory().size() == 1);
        check("restart keeps the cancellation details", "admin".equals(restored.findJob(9).getCancelledBy())
                && "Emergency maintenance on GPU2.".equals(restored.findJob(9).getCancellationReason()));
        check("nobody is logged in after a restart", restored.getAccountManager().getCurrentUser() == null);
        check("saving leaves no temporary file", !new File(folder, GpuSystem.SIMULATION_FILE + ".tmp").exists());
    }

    // Two copies of the same saved state must behave the same from then on.
    private static void testContinuityAfterRestart() throws Exception {
        String folder = newFolder();
        GpuSystem original = scenario(folder);
        original.getAccountManager().login("admin", "gpu2026");

        String copy = newFolder();
        copyFolder(folder, copy);
        GpuSystem restored = new GpuSystem(copy);
        restored.getAccountManager().login("admin", "gpu2026");

        boolean same = true;

        for (int minute = 0; minute < 70; minute++) {
            original.advanceMinute();
            restored.advanceMinute();

            if (!snapshot(original).equals(snapshot(restored))) {
                same = false;
            }
        }

        check("70 minutes after a restart both copies are identical", same);

        Job a = original.getJobManager().submitJob("S003", "t", "a", "d", "Small", null);
        Job b = restored.getJobManager().submitJob("S003", "t", "a", "d", "Small", null);
        check("the next job ID continues after a restart", a != null && b != null && a.getJobId() == 23 && b.getJobId() == 23);
    }

    private static void testSpecialCharacters() throws Exception {
        String folder = newFolder();
        GpuSystem system = startedSystemIn(folder, 1, 100);
        String title = "Back\\slash | pipe";
        String description = "Line one\nLine two | with \\n text\r\nand Arabic: مرحبا";

        Job job = system.getJobManager().submitJob("S001", title, "App|2", description, "Small", null);
        system.getJobManager().cancelJob(job.getJobId(), "S001", "Reason with | and \\ and \\p", null, 0);

        GpuSystem restored = new GpuSystem(folder);
        Job back = restored.findJob(job.getJobId());

        check("| \\ and new lines survive a restart", back.getTitle().equals(title)
                && back.getApplicationName().equals("App|2") && back.getDescription().equals(description.trim())
                && back.getCancellationReason().equals("Reason with | and \\ and \\p"));
    }

    private static void testMissingAndDamagedFiles() throws Exception {
        GpuSystem empty = new GpuSystem(newFolder());
        check("a missing file means a fresh system", !empty.getClock().isInitialised() && empty.getAllJobs().isEmpty());

        String good = newFolder();
        scenario(good);
        String text = readFile(new File(good, GpuSystem.SIMULATION_FILE));

        check("bad header is reported", loadError(good, text.replace(StorageManager.HEADER, "SOMETHING_ELSE"), "line 1"));
        check("a non-number is reported with its line",
                loadError(good, text.replace("S001|100|40|30", "S001|100|forty|30"), "line"));
        check("an unknown status is reported",
                loadError(good, text.replace("|Queued|-|-|-|0|false", "|Waiting|-|-|-|0|false"), "unknown job status"));
        check("balances that do not match the jobs are reported",
                loadError(good, text.replace("S003|50|30|5", "S003|50|30|0"), "does not match its jobs"));
        check("a GPU that disagrees with the jobs is reported",
                loadError(good, text.replace("3|18|5", "3|18|6"), "GPU3"));
        check("a truncated file is reported", loadError(good, text.substring(0, text.indexOf("[CANCELLATIONS]")), "incomplete"));
        check("a cancelled job without a record is reported",
                loadError(good, text.replace("7|23/11/2026 09:03|S002|Submitted with the wrong data set.", ""), "no record"));
        check("a bad time is reported", loadError(good, text.replace("23/11/2026 09:41|Running", "41/11/2026 09:41|Running"), "dd/MM/yyyy"));

        // simulation.txt that refers to a student who is missing from accounts.txt
        String orphan = newFolder();
        writeFile(new File(orphan, GpuSystem.SIMULATION_FILE), text);
        boolean reported = false;

        try {
            new GpuSystem(orphan);
        } catch (StorageException e) {
            reported = e.getMessage().contains("not an approved account");
        }

        check("data for an unknown student is reported", reported);
    }

    // Random actions with restarts at random moments. After each restart the restored
    // system must equal the one before it, and the rules must hold after every step.
    private static void testRandomRestarts() throws Exception {
        int restarts = 0;
        boolean allSame = true;
        boolean rulesHold = true;

        for (int seed = 1; seed <= 3; seed++) {
            Random random = new Random(seed);
            String folder = newFolder();
            GpuSystem system = startedSystemIn(folder, 5, 300);

            for (int step = 0; step < 600; step++) {
                int action = random.nextInt(100);

                if (action < 35) {
                    String[] sizes = {"Small", "Medium", "Large", "Very large"};
                    system.getJobManager().submitJob("S00" + (1 + random.nextInt(5)), "job " + step, "app", "desc",
                            sizes[random.nextInt(4)], null);
                } else if (action < 45) {
                    cancelRandomJob(system, random);
                } else if (action < 47) {
                    system.grantExtraTime("S00" + (1 + random.nextInt(5)), 1 + random.nextInt(30), "test " + step);
                } else if (action < 52) {
                    String before = snapshot(system);
                    system = new GpuSystem(folder);       // restart from the files
                    system.getAccountManager().login("admin", "gpu2026");
                    restarts++;

                    if (!before.equals(snapshot(system))) {
                        allSame = false;
                    }
                } else {
                    system.advanceMinute();
                }

                if (!rulesHold(system)) {
                    rulesHold = false;
                }
            }
        }

        check("random restarts (" + restarts + ") restore exactly the same state", allSame && restarts > 30);
        check("balances never negative, reservations and charges always add up", rulesHold);
    }

    // =====================================================================
    // Search
    // =====================================================================

    private static void testSearch() throws Exception {
        GpuSystem system = scenario(newFolder());

        check("search by job number", ids(system.searchJobs("12", "All")).equals("12"));
        check("search by J-number", ids(system.searchJobs("j12", "All")).equals("12"));
        check("search by student ID", ids(system.searchJobs("S005", "All")).equals("5,11,14,18"));
        check("search ignores upper/lower case (PyTorch and PyTorch Geometric)",
                ids(system.searchJobs("pytorch", "All")).equals("2,7,12,13,17,21,22"));
        check("status filter", ids(system.searchJobs("", Scheduler.CANCELLED)).equals("7,8,9,14"));
        check("keyword and status together", ids(system.searchJobs("pytorch", Scheduler.QUEUED)).equals("21,22"));
        check("empty search shows every job", system.searchJobs("  ", "All").size() == 22);
        check("no match gives an empty list", system.searchJobs("no such job", "All").isEmpty());

        check("a student only finds their own jobs", ids(system.searchStudentJobs("S003", "", "All")).equals("3,8,13,19")
                && system.searchStudentJobs("S003", "pytorch", "All").size() == 1);
        check("a student cannot find another student's job by number", system.searchStudentJobs("S003", "12", "All").isEmpty());

        check("students by university ID", system.searchStudents("s00").size() == 5
                && system.searchStudents("003").size() == 1 && system.searchStudents("S006").isEmpty());
    }

    // =====================================================================
    // Test scenario
    // =====================================================================

    private static void testScenario() throws Exception {
        GpuSystem system = scenario(newFolder());

        check("scenario: 5 approved, 2 pending, 1 rejected", system.getStudents().size() == 5
                && system.countRequests("Pending") == 2 && system.countRequests("Rejected") == 1);
        check("scenario: 22 jobs in all four states", system.getAllJobs().size() == SCENARIO_JOBS
                && system.countJobs(Scheduler.QUEUED) == 4 && system.countJobs(Scheduler.RUNNING) == 4
                && system.countJobs(Scheduler.COMPLETED) == 10 && system.countJobs(Scheduler.CANCELLED) == 4);
        check("scenario: all four GPUs busy with jobs waiting", system.getBusyGpuCount() == 4
                && system.getScheduler().predictQueue().size() == 4);
        check("scenario: cancellations by student and administrator, queued and running",
                "S002".equals(system.findJob(7).getCancelledBy()) && system.findJob(7).getStartTime() == null
                && "S003".equals(system.findJob(8).getCancelledBy()) && system.findJob(8).getStartTime() != null
                && "admin".equals(system.findJob(9).getCancelledBy()) && system.findJob(9).getStartTime() != null
                && "admin".equals(system.findJob(14).getCancelledBy()) && system.findJob(14).getStartTime() == null);
        check("scenario: S005 has 0 minutes available", system.getAllowance("S005").getAvailableMinutes() == 0);
        check("scenario: S003 has exactly 15 minutes available", system.getAllowance("S003").getAvailableMinutes() == 15);
        check("scenario: the rules hold", rulesHold(system));
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static final int SCENARIO_JOBS = 22;

    // A full test scenario in a temporary folder, built by running the real logic:
    // 5 approved students (S001-S005), 2 pending requests (S006, S007), 1 rejected (S008)
    // and 22 jobs at 23/11/2026 10:03: 10 completed, 4 running (all GPUs busy), 4 waiting
    // and 4 cancelled (by a student and by the administrator, while queued and while running).
    // S005 ends with 0 minutes available and S003 with exactly 15.
    private static GpuSystem scenario(String folder) throws Exception {
        GpuSystem system = new GpuSystem(folder);
        AccountManager accounts = system.getAccountManager();

        accounts.register("S001", "Aisha Al Mansoori", "aisha.almansoori@example.com", "+971 50 111 0001", "pass001");
        accounts.register("S002", "Omar Haddad", "omar.haddad@example.com", "+971 50 111 0002", "pass002");
        accounts.register("S003", "Fatima Khan", "fatima.khan@example.com", "+971 50 111 0003", "pass003");
        accounts.register("S004", "Yousef Rahman", "yousef.rahman@example.com", "+971 50 111 0004", "pass004");
        accounts.register("S005", "Mariam Saleh", "mariam.saleh@example.com", "+971 50 111 0005", "pass005");
        accounts.register("S006", "Khalid Ibrahim", "khalid.ibrahim@example.com", "+971 50 111 0006", "pass006");
        accounts.register("S007", "Noor Hassan", "noor.hassan@example.com", "+971 50 111 0007", "pass007");
        accounts.register("S008", "Rashid Ali", "rashid.ali@example.com", "+971 50 111 0008", "pass008");

        accounts.login("admin", "gpu2026");
        accounts.approve("S001");
        accounts.approve("S002");
        accounts.approve("S003", 50);
        accounts.approve("S004", 200);
        accounts.approve("S005", 50);
        accounts.reject("S008");
        system.accountsChanged();
        system.initialiseClock(LocalDateTime.of(2026, 11, 23, 9, 0));

        advanceTo(system, 0);
        submit(system, "S001", "Data cleaning script", "Python (pandas)", "Removes duplicates and empty rows from the survey data set.", "Small");
        submit(system, "S002", "CNN baseline training", "PyTorch", "Trains a small convolutional network on CIFAR-10 as a baseline.", "Medium");
        submit(system, "S003", "CUDA matrix benchmark", "CUDA samples", "Measures matrix multiplication speed for the lab report.", "Small");
        submit(system, "S004", "BERT fine-tuning", "Hugging Face Transformers", "Fine-tunes BERT-base on the course sentiment data.", "Large");
        submit(system, "S005", "Image segmentation", "TensorFlow", "U-Net segmentation of road images, first full run.", "Medium");
        submit(system, "S001", "Hyperparameter sweep A", "scikit-learn", "Grid search over learning rates for the regression model.", "Small");
        advanceTo(system, 2);
        submit(system, "S002", "Data augmentation test", "PyTorch", "Tests random crops and flips on the training images.", "Large");
        advanceTo(system, 3);
        cancel(system, 7, "S002", "Submitted with the wrong data set.");                  // student, while queued
        advanceTo(system, 6);
        submit(system, "S003", "Protein structure demo", "OpenFold", "Predicts the structure of a short demo protein sequence.", "Medium");
        advanceTo(system, 12);
        submit(system, "S004", "Speech recognition training", "Whisper", "Trains a small speech model on the lab recordings.", "Very large");
        advanceTo(system, 20);
        cancel(system, 8, "S003", "Found an error in the input file.");                  // student, while running
        advanceTo(system, 21);
        submit(system, "S001", "Reinforcement learning agent", "Stable-Baselines3", "PPO agent for the cart-pole task, 200k steps.", "Large");
        advanceTo(system, 22);
        submit(system, "S005", "Segmentation v2 check", "TensorFlow", "Quick check of the improved segmentation model.", "Small");
        advanceTo(system, 24);
        submit(system, "S002", "Transformer from scratch", "PyTorch", "Trains a tiny transformer language model on course notes.", "Large");
        advanceTo(system, 26);
        submit(system, "S003", "Graph neural network", "PyTorch Geometric", "Node classification on the citation graph.", "Medium");
        advanceTo(system, 32);
        system.grantExtraTime("S002", 30, "Capstone project deadline extension approved by the course coordinator.");
        advanceTo(system, 35);
        submit(system, "S005", "Segmentation v2 check (copy)", "TensorFlow", "Same job as before, submitted twice by mistake.", "Small");
        advanceTo(system, 36);
        system.cancelJobAsAdmin(14, "Duplicate of job J11.");                            // administrator, while queued
        advanceTo(system, 40);
        system.cancelJobAsAdmin(9, "Emergency maintenance on GPU2.");                    // administrator, while running
        advanceTo(system, 41);
        submit(system, "S004", "Object detection", "YOLOv8", "Detects vehicles in the campus parking camera images.", "Large");
        advanceTo(system, 50);
        submit(system, "S001", "Hyperparameter sweep B", "scikit-learn", "Second grid search with a wider range of values.", "Medium");
        advanceTo(system, 52);
        submit(system, "S002", "Language model evaluation", "PyTorch", "Evaluates the transformer on the held-out test set.", "Medium");
        advanceTo(system, 58);
        submit(system, "S005", "Satellite image classifier", "TensorFlow", "Classifies land use in satellite images (uses the last 30 minutes).", "Large");
        advanceTo(system, 62);
        submit(system, "S003", "Molecular dynamics test", "GROMACS", "Short water box simulation to test the setup.", "Small");
        advanceTo(system, 63);
        submit(system, "S004", "Video captioning", "Hugging Face Transformers", "Generates captions for the lecture video clips.", "Very large");
        submit(system, "S001", "Model distillation", "PyTorch", "Distils the large model into a smaller student model.", "Medium");
        submit(system, "S002", "Ablation study", "PyTorch", "Removes one layer at a time to measure its effect.", "Large");

        accounts.logout();
        system.save();
        return system;
    }

    // Advances the clock until the given number of minutes after the start.
    private static void advanceTo(GpuSystem system, int minute) throws Exception {
        while (system.getClock().getElapsedMinutes() < minute) {
            system.advanceMinute();
        }
    }

    private static void submit(GpuSystem system, String student, String title, String application,
            String description, String size) {
        if (system.getJobManager().submitJob(student, title, application, description, size, null) == null) {
            throw new IllegalStateException("Scenario job was not accepted: " + title);
        }
    }

    private static void cancel(GpuSystem system, int jobId, String student, String reason) {
        if (!system.getJobManager().cancelJob(jobId, student, reason, null, 0)) {
            throw new IllegalStateException("Scenario cancellation failed: J" + jobId);
        }
    }

    // A system with n approved students (S001...) and the clock not started; admin logged in.
    private static GpuSystem systemWithStudents(int count, int minutes) throws Exception {
        return systemWithStudentsIn(newFolder(), count, minutes);
    }

    private static GpuSystem systemWithStudentsIn(String folder, int count, int minutes) throws Exception {
        GpuSystem system = new GpuSystem(folder);
        AccountManager accounts = system.getAccountManager();

        for (int i = 1; i <= count; i++) {
            accounts.register("S00" + i, "Student " + i, "s" + i + "@example.com", "0501234567", "secret" + i);
        }

        accounts.login("admin", "gpu2026");

        for (int i = 1; i <= count; i++) {
            accounts.approve("S00" + i, minutes);
        }

        system.accountsChanged();
        return system;
    }

    // The same, with the clock started at 23/11/2026 09:00.
    private static GpuSystem startedSystem(int count, int minutes) throws Exception {
        return startedSystemIn(newFolder(), count, minutes);
    }

    private static GpuSystem startedSystemIn(String folder, int count, int minutes) throws Exception {
        GpuSystem system = systemWithStudentsIn(folder, count, minutes);
        system.initialiseClock(LocalDateTime.of(2026, 11, 23, 9, 0));
        return system;
    }

    // Builds the state of the example dashboard in the project brief (time 10:20).
    private static GpuSystem exampleSystem() throws Exception {
        GpuSystem system = systemWithStudents(5, 100);
        IntegratedJobManager jobs = system.getJobManager();
        system.initialiseClock(LocalDateTime.of(2026, 11, 23, 10, 0));

        jobs.submitJob("S001", "t", "a", "d", "Medium", null);      // J1 GPU1 10:00-10:15
        jobs.submitJob("S002", "t", "a", "d", "Large", null);       // J2 GPU2 10:00-10:30
        jobs.submitJob("S003", "t", "a", "d", "Small", null);       // J3 GPU3 10:00-10:05
        jobs.submitJob("S004", "t", "a", "d", "Small", null);       // J4 GPU4 10:00-10:05
        advance(system, 5);
        jobs.submitJob("S005", "t", "a", "d", "Large", null);       // J5 GPU3 10:05-10:35
        advance(system, 5);
        jobs.submitJob("S001", "t", "a", "d", "Large", null);       // J6 GPU4 10:10-10:40
        advance(system, 10);
        jobs.submitJob("S002", "t", "a", "d", "Small", null);       // J7 GPU1 10:20-10:25
        jobs.submitJob("S003", "Model training", "a", "d", "Medium", null);   // J8 waits
        jobs.submitJob("S005", "Image processing", "a", "d", "Small", null);  // J9 waits
        return system;
    }

    private static Job[] fillGpus(IntegratedJobManager jobs, String size) {
        Job[] result = new Job[4];

        for (int i = 0; i < 4; i++) {
            result[i] = jobs.submitJob("S00" + (i + 1), "job" + (i + 1), "app", "desc", size, null);
        }

        return result;
    }

    private static void advance(GpuSystem system, int minutes) throws Exception {
        for (int i = 0; i < minutes; i++) {
            system.advanceMinute();
        }
    }

    private static int waitOf(GpuSystem system, int jobId) {
        return system.describeJob(system.findJob(jobId)).getRemainingWait();
    }

    private static void cancelRandomJob(GpuSystem system, Random random) {
        ArrayList<Job> active = new ArrayList<Job>();
        ArrayList<Job> all = system.getAllJobs();

        for (int i = 0; i < all.size(); i++) {
            String status = all.get(i).getStatus();

            if (status.equals(Scheduler.QUEUED) || status.equals(Scheduler.RUNNING)) {
                active.add(all.get(i));
            }
        }

        if (active.isEmpty()) {
            return;
        }

        Job job = active.get(random.nextInt(active.size()));
        String by = random.nextBoolean() ? "admin" : job.getStudentId();
        system.getJobManager().cancelJob(job.getJobId(), by, "random test", null, 0);
    }

    // The allowance and scheduling rules that must always be true.
    private static boolean rulesHold(GpuSystem system) {
        Scheduler scheduler = system.getScheduler();
        ArrayList<Job> jobs = system.getAllJobs();
        ArrayList<TimeAllowance> allowances = system.getJobManager().getAllowances();
        int busyMinutes = 0;

        for (int i = 0; i < allowances.size(); i++) {
            TimeAllowance allowance = allowances.get(i);
            int reserved = 0;
            int used = 0;

            for (int j = 0; j < jobs.size(); j++) {
                Job job = jobs.get(j);

                if (!job.getStudentId().equals(allowance.getStudentId())) {
                    continue;
                }

                if (job.isSettled()) {
                    used += job.getChargedMinutes();
                } else {
                    reserved += job.getEstimatedDuration();
                }
            }

            if (allowance.getAvailableMinutes() < 0 || reserved != allowance.getReservedMinutes()
                    || used != allowance.getUsedMinutes()) {
                return false;
            }
        }

        for (int j = 0; j < jobs.size(); j++) {
            Job job = jobs.get(j);
            boolean finished = job.getStatus().equals(Scheduler.COMPLETED) || job.getStatus().equals(Scheduler.CANCELLED);

            if (finished != job.isSettled()) {
                return false;       // settled exactly when the job has ended
            }

            if (job.getStatus().equals(Scheduler.RUNNING)) {
                busyMinutes += scheduler.getGpu(job.getAssignedGpuId()).getMinutesRun();

                if (scheduler.getGpu(job.getAssignedGpuId()).getCurrentJob() != job) {
                    return false;
                }
            } else if (job.getStartTime() != null) {
                busyMinutes += job.getChargedMinutes();
            }
        }

        // No GPU may stay free while a job waits, and the queue stays in FCFS order.
        ArrayList<Job> queue = scheduler.getQueue();

        if (!queue.isEmpty() && scheduler.busyGpuCount() < Scheduler.GPU_COUNT) {
            return false;
        }

        for (int i = 1; i < queue.size(); i++) {
            if (queue.get(i - 1).getJobId() > queue.get(i).getJobId()) {
                return false;
            }
        }

        return busyMinutes == scheduler.getTotalBusyGpuMinutes();
    }

    // Everything that must survive a restart, as text, so two systems can be compared.
    private static String snapshot(GpuSystem system) {
        StringBuilder text = new StringBuilder();
        SimulationClock clock = system.getClock();
        Scheduler scheduler = system.getScheduler();

        text.append("clock ").append(clock.isInitialised()).append(' ').append(clock.formattedNow())
                .append(' ').append(clock.getElapsedMinutes()).append(' ').append(scheduler.getTotalBusyGpuMinutes())
                .append(' ').append(system.getUtilisationText()).append('\n');

        if (clock.isInitialised()) {
            text.append("start ").append(SimulationClock.format(clock.getStart())).append('\n');
        }

        GPU[] gpus = scheduler.getGpus();

        for (int i = 0; i < gpus.length; i++) {
            text.append("gpu ").append(gpus[i].getId()).append(' ')
                    .append(gpus[i].isBusy() ? gpus[i].getCurrentJob().getJobId() : -1).append(' ')
                    .append(gpus[i].getMinutesRun()).append(' ').append(gpus[i].remainingRuntime()).append(' ')
                    .append(SimulationClock.format(gpus[i].expectedFinish())).append('\n');
        }

        text.append("queue ").append(queueIds(system)).append('\n');

        ArrayList<QueuePrediction> predictions = scheduler.predictQueue();

        for (int i = 0; i < predictions.size(); i++) {
            QueuePrediction p = predictions.get(i);
            text.append("prediction ").append(p.getJob().getJobId()).append(' ').append(p.getPosition()).append(' ')
                    .append(p.getPredictedGpuId()).append(' ').append(SimulationClock.format(p.getPredictedStart()))
                    .append(' ').append(SimulationClock.format(p.getPredictedFinish())).append(' ')
                    .append(p.getRemainingWaitMinutes()).append('\n');
        }

        ArrayList<Job> jobs = system.getAllJobs();

        for (int i = 0; i < jobs.size(); i++) {
            Job j = jobs.get(i);
            text.append("job ").append(j.getJobId()).append('|').append(j.getStudentId()).append('|')
                    .append(j.getTitle()).append('|').append(j.getApplicationName()).append('|')
                    .append(j.getDescription()).append('|').append(j.getTaskSize()).append('|')
                    .append(j.getWorkloadUnits()).append('|').append(j.getEstimatedDuration()).append('|')
                    .append(j.getSubmissionTime()).append('|').append(j.getStatus()).append('|')
                    .append(j.getAssignedGpuId()).append('|').append(j.getStartTime()).append('|')
                    .append(j.getFinishTime()).append('|').append(j.getCancellationTime()).append('|')
                    .append(j.getCancelledBy()).append('|').append(j.getCancellationReason()).append('|')
                    .append(j.getChargedMinutes()).append('|').append(j.isSettled()).append('\n');
        }

        ArrayList<TimeAllowance> allowances = system.getJobManager().getAllowances();

        for (int i = 0; i < allowances.size(); i++) {
            TimeAllowance a = allowances.get(i);
            text.append("allowance ").append(a).append(' ').append(a.getGrantHistory()).append('\n');
        }

        return text.toString();
    }

    private static String queueIds(GpuSystem system) {
        return ids(system.getScheduler().getQueue());
    }

    private static String ids(ArrayList<Job> jobs) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < jobs.size(); i++) {
            if (i > 0) {
                text.append(',');
            }

            text.append(jobs.get(i).getJobId());
        }

        return text.toString();
    }

    private static Job[] currentGpuJobs(GpuSystem system) {
        Job[] result = new Job[4];

        for (int i = 0; i < 4; i++) {
            result[i] = system.getScheduler().getGpu(i + 1).getCurrentJob();
        }

        return result;
    }

    private static boolean sameJobs(Job[] a, Job[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }

        return true;
    }

    // Writes a damaged simulation.txt next to a good accounts.txt and checks the error message.
    private static boolean loadError(String goodFolder, String damagedText, String expected) throws Exception {
        String folder = newFolder();
        Files.copy(new File(goodFolder, GpuSystem.ACCOUNTS_FILE).toPath(), new File(folder, GpuSystem.ACCOUNTS_FILE).toPath());
        writeFile(new File(folder, GpuSystem.SIMULATION_FILE), damagedText);

        try {
            new GpuSystem(folder);
            return false;
        } catch (StorageException e) {
            return e.getMessage().contains(expected) && e.getMessage().contains(GpuSystem.SIMULATION_FILE);
        }
    }

    private static boolean throwsAccountError(AccountManager accounts, String id, String name, String email,
            String phone, String password) {
        try {
            accounts.register(id, name, email, phone, password);
            return false;
        } catch (AccountException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean throwsAccountException(GpuSystem system, LocalDateTime start) {
        try {
            system.initialiseClock(start);
            return false;
        } catch (AccountException e) {
            return true;
        }
    }

    private static boolean throwsIllegalArgument(GpuSystem system, String id, int minutes, String reason) throws Exception {
        try {
            system.grantExtraTime(id, minutes, reason);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean canLogin(AccountManager accounts, String user, String password) {
        try {
            accounts.login(user, password);
            accounts.logout();
            return true;
        } catch (AccountException e) {
            return false;
        }
    }

    private static String repeat(char c, int count) {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < count; i++) {
            text.append(c);
        }

        return text.toString();
    }

    private static String newFolder() throws IOException {
        File folder = Files.createTempDirectory("gpu-test").toFile();
        folders.add(folder);
        return folder.getPath();
    }

    private static void copyFolder(String from, String to) throws IOException {
        File[] files = new File(from).listFiles();

        for (int i = 0; i < files.length; i++) {
            Files.copy(files[i].toPath(), new File(to, files[i].getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), UTF8);
    }

    private static void writeFile(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(UTF8));
    }

    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                files[i].delete();
            }
        }

        folder.delete();
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " ==");
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("x FAIL  " + label);
        }
    }
}
