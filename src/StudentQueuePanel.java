import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// Student tab "Queue status and results": only the logged-in student's own jobs, with the
// assigned GPU, queue position, predicted (or actual) start and finish, waiting time,
// runtime and charge, plus a search. It sits next to Person 2's StudentDashboardPanel.
public class StudentQueuePanel extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;

    private GpuSystem system;
    private String studentId;

    private JLabel waitingValue = new JLabel();
    private JLabel runningValue = new JLabel();
    private JLabel completedValue = new JLabel();
    private JLabel cancelledValue = new JLabel();
    private JLabel availableValue = new JLabel();
    private JLabel nextLabel = UiStyle.label(" ");

    private JTextField searchField = new JTextField(16);
    private JComboBox<String> statusBox = new JComboBox<String>(new String[] {
        "All", Scheduler.QUEUED, Scheduler.RUNNING, Scheduler.COMPLETED, Scheduler.CANCELLED
    });
    private JButton searchButton;
    private JButton showAllButton;
    private JButton detailsButton;

    private DefaultTableModel model = UiStyle.tableModel(new String[] {
        "Job", "Title", "Application", "Size", "Status", "GPU", "Position", "Submitted",
        "Start", "Finish", "Wait", "Runtime", "Charged"
    });
    private JTable table = UiStyle.table(model);
    private ArrayList<Job> shownJobs = new ArrayList<Job>();

    public StudentQueuePanel(GpuSystem system, String studentId) {
        this.system = system;
        this.studentId = studentId;

        setLayout(new BorderLayout(0, 10));
        setBackground(UiStyle.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(createSummaryRow(), BorderLayout.NORTH);
        top.add(createSearchBar(), BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        UiStyle.makeCompact(table);
        UiStyle.setColumnWidths(table, new int[] {42, 170, 135, 72, 78, 46, 60, 118, 118, 118, 60, 62, 62});
        add(UiStyle.scroll(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        detailsButton = UiStyle.button("View details", this);
        bottom.add(detailsButton, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);

        refresh();
    }

    private JPanel createSummaryRow() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        JPanel row = new JPanel(new GridLayout(1, 5, 10, 0));
        row.setOpaque(false);
        row.add(UiStyle.statCard("Waiting", waitingValue));
        row.add(UiStyle.statCard("Running", runningValue));
        row.add(UiStyle.statCard("Completed", completedValue));
        row.add(UiStyle.statCard("Cancelled", cancelledValue));
        row.add(UiStyle.statCard("Available minutes", availableValue));

        panel.add(row, BorderLayout.CENTER);
        panel.add(nextLabel, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createSearchBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(UiStyle.CARD);
        bar.setBorder(UiStyle.cardBorder(6));

        searchButton = UiStyle.button("Search", this);
        showAllButton = UiStyle.button("Show all", this);
        searchField.addActionListener(this);
        statusBox.addActionListener(this);

        bar.add(UiStyle.label("Search my jobs:"));
        bar.add(searchField);
        bar.add(UiStyle.label("Status:"));
        bar.add(statusBox);
        bar.add(searchButton);
        bar.add(showAllButton);
        return bar;
    }

    // Reads the student's jobs again. Called after every change in the system.
    public void refresh() {
        ArrayList<Job> mine = system.getStudentJobs(studentId);
        int waiting = 0;
        int running = 0;
        int completed = 0;
        int cancelled = 0;

        for (int i = 0; i < mine.size(); i++) {
            String status = mine.get(i).getStatus();

            if (status.equals(Scheduler.QUEUED)) {
                waiting++;
            } else if (status.equals(Scheduler.RUNNING)) {
                running++;
            } else if (status.equals(Scheduler.COMPLETED)) {
                completed++;
            } else {
                cancelled++;
            }
        }

        waitingValue.setText(Integer.toString(waiting));
        runningValue.setText(Integer.toString(running));
        completedValue.setText(Integer.toString(completed));
        cancelledValue.setText(Integer.toString(cancelled));

        TimeAllowance allowance = system.getAllowance(studentId);
        availableValue.setText((allowance == null) ? "-" : Integer.toString(allowance.getAvailableMinutes()));

        nextLabel.setText(describeNextStart());

        Job selected = selectedJob();
        shownJobs = system.searchStudentJobs(studentId, searchField.getText(), (String) statusBox.getSelectedItem());
        ArrayList<JobInfo> infos = system.describeJobs(shownJobs);

        model.setRowCount(0);

        for (int i = 0; i < infos.size(); i++) {
            JobInfo info = infos.get(i);
            Job job = info.getJob();

            model.addRow(new Object[] {
                info.getJobLabel(),
                job.getTitle(),
                job.getApplicationName(),
                job.getTaskSize(),
                job.getStatus(),
                info.getGpuText(),
                info.getPositionText(),
                job.getSubmissionTime(),
                info.getStartText(),
                info.getFinishText(),
                info.getRemainingWaitText(),
                info.getRuntimeText(),
                info.getChargedText()
            });
        }

        int row = shownJobs.indexOf(selected);

        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        }
    }

    // A one-line summary of the student's next waiting job, if any.
    private String describeNextStart() {
        if (!system.getClock().isInitialised()) {
            return "The simulation clock has not started yet, so jobs cannot be submitted.";
        }

        ArrayList<JobInfo> infos = system.describeJobs(system.searchStudentJobs(studentId, "", Scheduler.QUEUED));

        if (infos.isEmpty()) {
            return "You have no jobs waiting in the queue.";
        }

        JobInfo next = infos.get(0);

        return "Your next waiting job " + next.getJobLabel() + " is number " + next.getPositionText()
                + " in the queue: predicted start " + next.getStartText()
                + " (in " + next.getRemainingWait() + " min), predicted finish " + next.getFinishText() + ".";
    }

    private Job selectedJob() {
        int row = table.getSelectedRow();

        if (row < 0 || row >= shownJobs.size()) {
            return null;
        }

        return shownJobs.get(row);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();

        if (source == searchButton || source == searchField || source == statusBox) {
            refresh();
        } else if (source == showAllButton) {
            searchField.setText("");
            statusBox.setSelectedIndex(0);
            refresh();
        } else if (source == detailsButton) {
            Job job = selectedJob();

            if (job == null) {
                JOptionPane.showMessageDialog(this, "Select a job in the table first.");
            } else {
                UiStyle.showText(this, "Job details", system.describeJob(job).describe());
            }
        }
    }
}
