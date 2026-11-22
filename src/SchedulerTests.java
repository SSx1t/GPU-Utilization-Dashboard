import java.time.LocalDateTime;
import java.util.ArrayList;

// Tests for the scheduler. Each check() prints PASS or FAIL.
// Run: javac src/*.java -d out  &&  java -cp out SchedulerTests.
public class SchedulerTests {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        testFcfsAndGpuOrder();
        testQueueOrdering();
        testPredictions();
        testAdvanceCompletesAndStarts();
        testUtilisation();
        testRunningCancellation();
        testQueuedCancellation();

        System.out.println("------------------------------------------");
        System.out.println("Passed: " + passed + "   Failed: " + failed);
        if (failed > 0) System.exit(1);
    }

    // Five jobs fill GPU1..4 in order, the fifth waits.
    private static void testFcfsAndGpuOrder() {
        Scheduler s = fresh(at(10, 0));
        Job j1 = small(101, "S001");
        Job j2 = small(102, "S002");
        Job j3 = small(103, "S003");
        Job j4 = small(104, "S004");
        Job j5 = small(105, "S005");
        s.submit(j1); s.submit(j2); s.submit(j3); s.submit(j4); s.submit(j5);

        check("J101 on GPU1", s.getGpu(1).getCurrentJob() == j1);
        check("J102 on GPU2", s.getGpu(2).getCurrentJob() == j2);
        check("J103 on GPU3", s.getGpu(3).getCurrentJob() == j3);
        check("J104 on GPU4", s.getGpu(4).getCurrentJob() == j4);
        check("J105 stays queued", s.getQueue().size() == 1 && s.getQueue().get(0) == j5);
        check("J105 status Queued", j5.getStatus().equals(Scheduler.QUEUED));
        check("4 GPUs busy", s.busyGpuCount() == 4);
    }

    // Queue keeps jobs in submission (id) order.
    private static void testQueueOrdering() {
        Scheduler s = fresh(at(10, 0));
        for (int id = 1; id <= 4; id++) s.submit(large(10 + id, "S00" + id));

        Job a = small(700, "S101");
        Job b = small(699, "S102");
        s.submit(a);                 // submitted first but larger id
        s.submit(b);
        ArrayList<Job> q1 = s.getQueue();
        check("queue sorted by id: 699 before 700", q1.get(0) == b && q1.get(1) == a);

        s.advanceMinute();
        Job c = small(800, "S103");  // later and larger id -> goes last
        s.submit(c);
        ArrayList<Job> q2 = s.getQueue();
        check("later job with larger id goes last", q2.get(2) == c);
    }

    // Predictions when all GPUs are busy.
    private static void testPredictions() {
        Scheduler s = fresh(at(10, 0));
        for (int id = 1; id <= 4; id++) s.submit(small(100 + id, "S00" + id));
        Job q1 = small(105, "S005");
        Job q2 = small(106, "S006");
        s.submit(q1);
        s.submit(q2);

        ArrayList<QueuePrediction> preds = s.predictQueue();
        check("two predictions", preds.size() == 2);

        QueuePrediction p1 = preds.get(0);
        QueuePrediction p2 = preds.get(1);
        check("pos1 is J105", p1.getJob() == q1 && p1.getPosition() == 1);
        check("J105 predicted start 10:05", p1.getPredictedStart().equals(at(10, 5)));
        check("J105 wait 5 min", p1.getRemainingWaitMinutes() == 5);
        check("J105 predicted finish 10:10", p1.getPredictedFinish().equals(at(10, 10)));
        check("pos2 is J106", p2.getJob() == q2 && p2.getPosition() == 2);
        check("J106 predicted start 10:05 (2nd free GPU)",
                p2.getPredictedStart().equals(at(10, 5)));
        check("J106 wait 5 min", p2.getRemainingWaitMinutes() == 5);
    }

    // Advancing completes jobs at the right minute and starts the waiting one.
    private static void testAdvanceCompletesAndStarts() {
        Recorder rec = new Recorder();
        Scheduler s = fresh(at(10, 0));
        s.setListener(rec);
        Job[] running = new Job[4];
        for (int i = 0; i < 4; i++) {
            running[i] = small(101 + i, "S00" + (i + 1));
            s.submit(running[i]);
        }
        Job queued = small(105, "S005");
        s.submit(queued);

        for (int i = 0; i < 5; i++) s.advanceMinute();   // -> 10:05

        check("clock at 10:05", s.getClock().now().equals(at(10, 5)));
        boolean allDone = true;
        for (int i = 0; i < running.length; i++) {
            if (!running[i].getStatus().equals(Scheduler.COMPLETED)
                    || !running[i].getFinishTime().equals("20/11/2026 10:05")) {
                allDone = false;
            }
        }
        check("all four completed at 10:05", allDone);
        boolean allFive = (rec.completedRuntimes.size() == 4);
        for (int i = 0; i < rec.completedRuntimes.size(); i++) {
            if (rec.completedRuntimes.get(i) != 5) allFive = false;
        }
        check("4 completion charges of 5 min", allFive);
        check("queued J105 now running on GPU1", s.getGpu(1).getCurrentJob() == queued);
        check("J105 started at 10:05", queued.getStartTime().equals("20/11/2026 10:05"));
        check("queue now empty", s.getQueue().isEmpty());
    }

    // Utilisation is N/A at the start, then busy/(4*elapsed).
    private static void testUtilisation() {
        Scheduler s = fresh(at(10, 0));
        check("N/A at time zero",
                s.utilisationPercent() < 0 && s.utilisationText().equals("N/A"));

        for (int i = 0; i < 4; i++) s.submit(small(101 + i, "S00" + (i + 1)));
        for (int i = 0; i < 5; i++) s.advanceMinute();

        check("busy GPU-minutes == 20", s.getTotalBusyGpuMinutes() == 20);
        check("elapsed == 5", s.getClock().getElapsedMinutes() == 5);
        check("utilisation == 100%", Math.abs(s.utilisationPercent() - 100.0) < 1e-9);
    }

    // Cancelling a running job charges the time used and starts the next job.
    private static void testRunningCancellation() {
        Recorder rec = new Recorder();
        Scheduler s = fresh(at(10, 0));
        s.setListener(rec);
        Job big = large(201, "S001");
        s.submit(big);
        for (int i = 2; i <= 4; i++) s.submit(small(200 + i, "S00" + i));
        Job waiter = small(205, "S005");
        s.submit(waiter);

        for (int i = 0; i < 3; i++) s.advanceMinute();   // -> 10:03

        boolean ok = s.cancelRunning(big, "admin", "pre-empted for maintenance");
        check("cancelRunning returned true", ok);
        check("J201 cancelled", big.getStatus().equals(Scheduler.CANCELLED));
        check("J201 finish time 10:03", big.getFinishTime().equals("20/11/2026 10:03"));
        check("charged elapsed runtime 3 min", rec.runningCancelRuntime == 3);
        check("GPU1 now runs the waiter J205", s.getGpu(1).getCurrentJob() == waiter);
        check("J205 started at 10:03", waiter.getStartTime().equals("20/11/2026 10:03"));
    }

    // Cancelling a queued job frees nothing on the GPUs.
    private static void testQueuedCancellation() {
        Recorder rec = new Recorder();
        Scheduler s = fresh(at(10, 0));
        s.setListener(rec);
        for (int i = 0; i < 4; i++) s.submit(small(301 + i, "S00" + (i + 1)));
        Job queued = small(305, "S005");
        s.submit(queued);

        Job gpu1Job = s.getGpu(1).getCurrentJob();
        boolean ok = s.cancelQueued(queued, "S005", "changed my mind");
        check("cancelQueued returned true", ok);
        check("J305 cancelled", queued.getStatus().equals(Scheduler.CANCELLED));
        check("queue now empty", s.getQueue().isEmpty());
        check("queued cancel fired, no charge", rec.queuedCancelCount == 1);
        check("running GPUs untouched",
                s.getGpu(1).getCurrentJob() == gpu1Job && s.busyGpuCount() == 4);
    }

    // --- helpers ---

    private static Scheduler fresh(LocalDateTime start) {
        SimulationClock clock = new SimulationClock();
        clock.initialise(start);
        return new Scheduler(clock);
    }

    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 11, 20, h, m);
    }

    private static Job small(int id, String student) {
        return new Job(id, student, "t", "app", "d", "Small", "20/11/2026 10:00");
    }

    private static Job large(int id, String student) {
        return new Job(id, student, "t", "app", "d", "Large", "20/11/2026 10:00");
    }

    private static void check(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("x FAIL  " + label);
        }
    }

    // Records what the scheduler reported, so the tests can check it.
    private static class Recorder implements SchedulerListener {
        ArrayList<Integer> completedRuntimes = new ArrayList<Integer>();
        int runningCancelRuntime = -1;
        int queuedCancelCount = 0;

        public void onJobCompleted(Job job, int runtimeMinutes) {
            completedRuntimes.add(runtimeMinutes);
        }

        public void onRunningCancelled(Job job, int runtimeMinutes) {
            runningCancelRuntime = runtimeMinutes;
        }

        public void onQueuedCancelled(Job job) {
            queuedCancelCount++;
        }
    }
}
