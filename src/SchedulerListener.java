// Called by the scheduler when a job finishes or is cancelled (used for allowances).
public interface SchedulerListener {

    void onJobCompleted(Job job, int runtimeMinutes);

    void onRunningCancelled(Job job, int runtimeMinutes);

    void onQueuedCancelled(Job job);
}
