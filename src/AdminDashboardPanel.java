import java.awt.*;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// The administrator's main dashboard: summary numbers, the four GPUs, the active jobs
// with their predictions, the completed jobs and every student's balance.
// It only reads from GpuSystem; refresh() redraws it.
public class AdminDashboardPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private GpuSystem system;

    // Summary row
    private JLabel runningValue = new JLabel();
    private JLabel queuedValue = new JLabel();
    private JLabel completedValue = new JLabel();
    private JLabel cancelledValue = new JLabel();
    private JLabel busyValue = new JLabel();
    private JLabel utilisationValue = new JLabel();

    // One entry per GPU card
    private JLabel[] gpuStatus = new JLabel[Scheduler.GPU_COUNT];
    private JLabel[] gpuJobs = new JLabel[Scheduler.GPU_COUNT];
    private JLabel[] gpuStudents = new JLabel[Scheduler.GPU_COUNT];
    private JLabel[] gpuRemaining = new JLabel[Scheduler.GPU_COUNT];
    private JLabel[] gpuFinish = new JLabel[Scheduler.GPU_COUNT];

    private DefaultTableModel activeModel = UiStyle.tableModel(new String[] {
        "Job", "Student", "Application", "Status", "Duration", "Position", "Start", "Finish", "Wait"
    });

    private DefaultTableModel completedModel = UiStyle.tableModel(new String[] {
        "Job", "Student", "GPU", "Submitted", "Start", "Finish", "Runtime", "Queue wait", "Charged"
    });

    private DefaultTableModel allowanceModel = UiStyle.tableModel(new String[] {
        "Student", "Name", "Allocated", "Used", "Reserved", "Available"
    });

    public AdminDashboardPanel(GpuSystem system) {
        this.system = system;

        setLayout(new GridBagLayout());
        setBackground(UiStyle.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(8, 2, 2, 2));

        // Four rows: summary and GPUs keep their size; the tables share the rest (55% / 45%).
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 8, 0);

        c.gridy = 0;
        c.weighty = 0;
        add(createSummaryRow(), c);

        c.gridy = 1;
        add(createGpuRow(), c);

        c.gridy = 2;
        c.weighty = 0.55;
        add(createActiveJobsCard(), c);

        c.gridy = 3;
        c.weighty = 0.45;
        c.insets = new Insets(0, 0, 0, 0);
        add(createBottomRow(), c);

        refresh();
    }

    private JPanel createSummaryRow() {
        JPanel row = new JPanel(new GridLayout(1, 6, 10, 0));
        row.setOpaque(false);

        row.add(UiStyle.statCard("Running", runningValue));
        row.add(UiStyle.statCard("Queued", queuedValue));
        row.add(UiStyle.statCard("Completed", completedValue));
        row.add(UiStyle.statCard("Cancelled", cancelledValue));
        row.add(UiStyle.statCard("Busy GPUs", busyValue));
        row.add(UiStyle.statCard("Utilization", utilisationValue));

        return row;
    }

    private JPanel createGpuRow() {
        JPanel card = UiStyle.card("GPU status");
        card.setBorder(UiStyle.cardBorder(8));

        JPanel row = new JPanel(new GridLayout(1, Scheduler.GPU_COUNT, 10, 0));
        row.setOpaque(false);

        for (int i = 0; i < Scheduler.GPU_COUNT; i++) {
            row.add(createGpuCard(i));
        }

        card.add(row, BorderLayout.CENTER);
        return card;
    }

    // One GPU: its name, then five rows of details.
    private JPanel createGpuCard(int index) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(UiStyle.CARD);
        card.setBorder(UiStyle.cardBorder(8));

        JLabel name = new JLabel("GPU" + (index + 1));
        name.setFont(UiStyle.SECTION_FONT);
        name.setForeground(UiStyle.HEADING);

        JPanel rows = new JPanel(new GridLayout(5, 2, 4, 1));
        rows.setBackground(UiStyle.CARD);

        gpuStatus[index] = addGpuRow(rows, "Status:");
        gpuJobs[index] = addGpuRow(rows, "Current job:");
        gpuStudents[index] = addGpuRow(rows, "Student:");
        gpuRemaining[index] = addGpuRow(rows, "Time remaining:");
        gpuFinish[index] = addGpuRow(rows, "Finish:");

        card.add(name, BorderLayout.NORTH);
        card.add(rows, BorderLayout.CENTER);
        return card;
    }

    private JLabel addGpuRow(JPanel rows, String text) {
        JLabel name = new JLabel(text);
        name.setFont(UiStyle.TEXT_FONT);

        JLabel value = new JLabel("-", SwingConstants.RIGHT);
        value.setFont(UiStyle.LABEL_FONT);

        rows.add(name);
        rows.add(value);
        return value;
    }

    private JPanel createActiveJobsCard() {
        JTable table = UiStyle.table(activeModel);
        table.setRowHeight(24);
        table.setPreferredScrollableViewportSize(new Dimension(400, 100));
        UiStyle.setColumnWidths(table, new int[] {55, 75, 190, 80, 75, 75, 90, 90, 80});

        JPanel card = UiStyle.card("Active jobs (waiting and running)");
        card.setBorder(UiStyle.cardBorder(8));
        card.add(UiStyle.scroll(table), BorderLayout.CENTER);
        return card;
    }

    private JPanel createBottomRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        JTable completed = UiStyle.table(completedModel);
        UiStyle.makeCompact(completed);
        completed.setPreferredScrollableViewportSize(new Dimension(400, 100));
        UiStyle.setColumnWidths(completed, new int[] {45, 60, 55, 85, 70, 70, 70, 80, 70});

        JPanel completedCard = UiStyle.card("Completed jobs");
        completedCard.setBorder(UiStyle.cardBorder(8));
        completedCard.add(UiStyle.scroll(completed), BorderLayout.CENTER);

        JTable allowances = UiStyle.table(allowanceModel);
        UiStyle.makeCompact(allowances);
        allowances.setPreferredScrollableViewportSize(new Dimension(300, 100));
        UiStyle.setColumnWidths(allowances, new int[] {65, 140, 75, 55, 70, 75});

        JPanel allowanceCard = UiStyle.card("Student allowances (minutes)");
        allowanceCard.setBorder(UiStyle.cardBorder(8));
        allowanceCard.add(UiStyle.scroll(allowances), BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;

        c.gridx = 0;
        c.weightx = 0.58;
        c.insets = new Insets(0, 0, 0, 5);
        row.add(completedCard, c);

        c.gridx = 1;
        c.weightx = 0.42;
        c.insets = new Insets(0, 5, 0, 0);
        row.add(allowanceCard, c);

        return row;
    }

    // Reads everything again from GpuSystem.
    public void refresh() {
        SimulationClock clock = system.getClock();

        runningValue.setText(Integer.toString(system.countJobs(Scheduler.RUNNING)));
        queuedValue.setText(Integer.toString(system.countJobs(Scheduler.QUEUED)));
        completedValue.setText(Integer.toString(system.countJobs(Scheduler.COMPLETED)));
        cancelledValue.setText(Integer.toString(system.countJobs(Scheduler.CANCELLED)));
        busyValue.setText(system.getBusyGpuCount() + " / " + Scheduler.GPU_COUNT);
        utilisationValue.setText(system.getUtilisationText());

        GPU[] gpus = system.getScheduler().getGpus();

        for (int i = 0; i < gpus.length; i++) {
            GPU gpu = gpus[i];

            if (gpu.isBusy()) {
                Job job = gpu.getCurrentJob();
                gpuStatus[i].setText("Busy");
                gpuJobs[i].setText("J" + job.getJobId());
                gpuStudents[i].setText(job.getStudentId());
                gpuRemaining[i].setText(gpu.remainingRuntime() + " min");
                gpuFinish[i].setText(UiStyle.shortTime(gpu.expectedFinish(), clock));
            } else {
                gpuStatus[i].setText("Free");
                gpuJobs[i].setText("-");
                gpuStudents[i].setText("-");
                gpuRemaining[i].setText("-");
                gpuFinish[i].setText("-");
            }
        }

        activeModel.setRowCount(0);
        ArrayList<JobInfo> active = system.getActiveJobs();

        for (int i = 0; i < active.size(); i++) {
            JobInfo info = active.get(i);
            Job job = info.getJob();

            activeModel.addRow(new Object[] {
                info.getJobLabel(),
                job.getStudentId(),
                job.getApplicationName(),
                job.getStatus(),
                info.getDurationText(),
                info.getPositionText(),
                UiStyle.shortTime(info.getStart(), clock),
                UiStyle.shortTime(info.getFinish(), clock),
                info.getRemainingWaitText()
            });
        }

        completedModel.setRowCount(0);
        ArrayList<JobInfo> completed = system.getCompletedJobs();

        for (int i = 0; i < completed.size(); i++) {
            JobInfo info = completed.get(i);
            Job job = info.getJob();

            completedModel.addRow(new Object[] {
                info.getJobLabel(),
                job.getStudentId(),
                info.getGpuText(),
                UiStyle.shortTime(job.getSubmissionTime(), clock),
                UiStyle.shortTime(info.getStart(), clock),
                UiStyle.shortTime(info.getFinish(), clock),
                info.getRuntimeText(),
                info.getQueueWaitText(),
                info.getChargedText()
            });
        }

        allowanceModel.setRowCount(0);
        ArrayList<Student> students = system.getStudents();

        for (int i = 0; i < students.size(); i++) {
            Student student = students.get(i);
            TimeAllowance allowance = system.getAllowance(student.getUsername());

            if (allowance != null) {
                allowanceModel.addRow(new Object[] {
                    student.getUsername(),
                    student.getName(),
                    allowance.getAllocatedMinutes(),
                    allowance.getUsedMinutes(),
                    allowance.getReservedMinutes(),
                    allowance.getAvailableMinutes()
                });
            }
        }
    }
}
