import java.time.LocalDateTime;

// Predicted start and finish for one waiting job.
public class QueuePrediction {

    private Job job;
    private int position;
    private int predictedGpuId;
    private LocalDateTime predictedStart;
    private LocalDateTime predictedFinish;
    private int remainingWaitMinutes;

    public QueuePrediction(Job job, int position, int predictedGpuId,
                           LocalDateTime predictedStart, LocalDateTime predictedFinish,
                           int remainingWaitMinutes) {
        this.job = job;
        this.position = position;
        this.predictedGpuId = predictedGpuId;
        this.predictedStart = predictedStart;
        this.predictedFinish = predictedFinish;
        this.remainingWaitMinutes = remainingWaitMinutes;
    }

    public Job getJob() {
        return job;
    }

    public int getPosition() {
        return position;
    }

    public int getPredictedGpuId() {
        return predictedGpuId;
    }

    public LocalDateTime getPredictedStart() {
        return predictedStart;
    }

    public LocalDateTime getPredictedFinish() {
        return predictedFinish;
    }

    public int getRemainingWaitMinutes() {
        return remainingWaitMinutes;
    }
}
