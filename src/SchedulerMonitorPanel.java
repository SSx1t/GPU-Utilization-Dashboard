import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.*;

// Screen that shows the GPUs and the queue. The logic is all in Scheduler.
public class SchedulerMonitorPanel extends JPanel implements ActionListener {

    private Scheduler scheduler;

    private JLabel clockLabel;
    private JTextField startField;
    private JButton initButton;
    private JButton advanceButton;

    private JTextArea gpuArea;
    private JTextArea waitingArea;
    private JLabel summaryLabel;

    public SchedulerMonitorPanel(Scheduler scheduler) {
        this.scheduler = scheduler;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Top bar: clock, start time field and the two buttons.
        JPanel top = new JPanel();
        top.add(new JLabel("Simulated time:"));
        clockLabel = new JLabel();
        clockLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        top.add(clockLabel);
        top.add(new JLabel("    Start (dd/MM/yyyy HH:mm):"));
        startField = new JTextField(16);
        top.add(startField);
        initButton = new JButton("Initialise clock");
        advanceButton = new JButton("Advance Minute (+1)");
        top.add(initButton);
        top.add(advanceButton);
        add(top, BorderLayout.NORTH);

        // Middle: GPU status and waiting jobs.
        gpuArea = makeArea();
        waitingArea = makeArea();
        JPanel tables = new JPanel(new GridLayout(2, 1, 0, 10));
        tables.add(titled("GPU status", gpuArea));
        tables.add(titled("Waiting jobs", waitingArea));
        add(tables, BorderLayout.CENTER);

        // Bottom: counts and utilisation.
        summaryLabel = new JLabel();
        summaryLabel.setBorder(BorderFactory.createEmptyBorder(6, 2, 6, 2));
        add(summaryLabel, BorderLayout.SOUTH);

        initButton.addActionListener(this);
        advanceButton.addActionListener(this);

        refresh();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        try {
            if (e.getSource() == initButton) {
                scheduler.getClock().initialiseFromText(startField.getText());
            } else if (e.getSource() == advanceButton) {
                scheduler.advanceMinute();
            }
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Enter the start time as dd/MM/yyyy HH:mm (e.g. 19/11/2026 10:00).",
                    "Scheduler", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Re-read everything from the scheduler. Call after submit/cancel/advance.
    public void refresh() {
        boolean ready = scheduler.getClock().isInitialised();
        advanceButton.setEnabled(ready);
        initButton.setEnabled(!ready);
        startField.setEnabled(!ready);
        clockLabel.setText(scheduler.getClock().formattedNow());

        StringBuilder g = new StringBuilder();
        g.append(String.format("%-6s %-10s %-8s %-14s %-18s%n",
                "GPU", "Status", "Job", "Remaining(min)", "Expected finish"));
        GPU[] gpus = scheduler.getGpus();
        for (int i = 0; i < gpus.length; i++) {
            GPU gpu = gpus[i];
            if (gpu.isFree()) {
                g.append(String.format("%-6s %-10s %-8s %-14s %-18s%n",
                        "GPU" + gpu.getId(), "Free", "-", "-", "-"));
            } else {
                g.append(String.format("%-6s %-10s %-8s %-14d %-18s%n",
                        "GPU" + gpu.getId(), "Busy",
                        "J" + gpu.getCurrentJob().getJobId(),
                        gpu.remainingRuntime(),
                        SimulationClock.format(gpu.expectedFinish())));
            }
        }
        gpuArea.setText(g.toString());

        StringBuilder w = new StringBuilder();
        w.append(String.format("%-6s %-8s %-16s %-10s %-9s %-18s %-9s%n",
                "Job", "Student", "Application", "Dur(min)", "Position",
                "Predicted start", "Wait(min)"));
        ArrayList<QueuePrediction> preds = scheduler.predictQueue();
        for (int i = 0; i < preds.size(); i++) {
            QueuePrediction p = preds.get(i);
            Job j = p.getJob();
            w.append(String.format("%-6s %-8s %-16s %-10d %-9d %-18s %-9d%n",
                    "J" + j.getJobId(), j.getStudentId(), j.getApplicationName(),
                    j.getEstimatedDuration(), p.getPosition(),
                    SimulationClock.format(p.getPredictedStart()),
                    p.getRemainingWaitMinutes()));
        }
        if (preds.isEmpty()) {
            w.append("(no waiting jobs)");
        }
        waitingArea.setText(w.toString());

        summaryLabel.setText(String.format(
                "Queued: %d    Running: %d    Completed: %d    Cancelled: %d"
                + "    Busy GPUs: %d/4    Utilisation: %s",
                scheduler.countByStatus(Scheduler.QUEUED),
                scheduler.countByStatus(Scheduler.RUNNING),
                scheduler.countByStatus(Scheduler.COMPLETED),
                scheduler.countByStatus(Scheduler.CANCELLED),
                scheduler.busyGpuCount(),
                scheduler.utilisationText()));
    }

    private JTextArea makeArea() {
        JTextArea a = new JTextArea(6, 70);
        a.setEditable(false);
        a.setFont(new Font("Monospaced", Font.PLAIN, 12));
        return a;
    }

    private JPanel titled(String title, JTextArea area) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        return p;
    }
}
