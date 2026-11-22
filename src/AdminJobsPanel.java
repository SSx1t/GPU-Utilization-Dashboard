import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

// "All jobs" tab: every job in the system (cancelled jobs stay in the history), with a
// keyword search, a status filter, a details dialog and cancellation with a reason.
public class AdminJobsPanel extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;

    private GpuSystem system;

    private JTextField searchField = new JTextField(20);
    private JComboBox<String> statusBox = new JComboBox<String>(new String[] {
        "All", Scheduler.QUEUED, Scheduler.RUNNING, Scheduler.COMPLETED, Scheduler.CANCELLED
    });

    private JButton searchButton;
    private JButton showAllButton;
    private JButton detailsButton;
    private JButton cancelButton;
    private JLabel countLabel = new JLabel();

    private DefaultTableModel model = UiStyle.tableModel(new String[] {
        "Job", "Student", "Title", "Application", "Size", "Status", "GPU", "Position",
        "Submitted", "Start", "Finish", "Wait", "Runtime", "Charged"
    });

    private JTable table = UiStyle.table(model);

    // The job shown in each table row (same order as the rows).
    private ArrayList<Job> shownJobs = new ArrayList<Job>();

    public AdminJobsPanel(GpuSystem system) {
        this.system = system;

        setLayout(new BorderLayout(0, 10));
        setBackground(UiStyle.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 2, 2, 2));

        add(createSearchBar(), BorderLayout.NORTH);

        UiStyle.makeCompact(table);
        UiStyle.setColumnWidths(table, new int[] {42, 58, 150, 125, 72, 78, 46, 60, 118, 118, 118, 60, 62, 62});
        add(UiStyle.scroll(table), BorderLayout.CENTER);

        add(createButtonBar(), BorderLayout.SOUTH);

        refresh();
    }

    private JPanel createSearchBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(UiStyle.CARD);
        bar.setBorder(UiStyle.cardBorder(6));

        searchButton = UiStyle.button("Search", this);
        showAllButton = UiStyle.button("Show all", this);

        searchField.addActionListener(this);    // Enter starts the search
        statusBox.addActionListener(this);      // a new status filters at once

        bar.add(UiStyle.label("Search jobs:"));
        bar.add(searchField);
        bar.add(UiStyle.label("Status:"));
        bar.add(statusBox);
        bar.add(searchButton);
        bar.add(showAllButton);

        return bar;
    }

    private JPanel createButtonBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);

        detailsButton = UiStyle.button("View details", this);
        cancelButton = UiStyle.button("Cancel selected job", UiStyle.RED_BUTTON, this);

        buttons.add(detailsButton);
        buttons.add(cancelButton);

        countLabel.setFont(UiStyle.LABEL_FONT);
        countLabel.setForeground(UiStyle.HEADING);

        bar.add(buttons, BorderLayout.WEST);
        bar.add(countLabel, BorderLayout.EAST);
        return bar;
    }

    // Runs the current search again (called after every change in the system).
    public void refresh() {
        String status = (String) statusBox.getSelectedItem();
        ArrayList<Job> jobs = system.searchJobs(searchField.getText(), status);
        ArrayList<JobInfo> infos = system.describeJobs(jobs);

        Job selected = selectedJob();

        model.setRowCount(0);
        shownJobs = jobs;

        for (int i = 0; i < infos.size(); i++) {
            JobInfo info = infos.get(i);
            Job job = info.getJob();

            model.addRow(new Object[] {
                info.getJobLabel(),
                job.getStudentId(),
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

        // Keep the same job selected after a refresh.
        int row = shownJobs.indexOf(selected);

        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
        }

        countLabel.setText("Showing " + shownJobs.size() + " of " + system.getAllJobs().size() + " jobs");
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
            showDetails();
        } else if (source == cancelButton) {
            cancelSelectedJob();
        }
    }

    private Job selectedJob() {
        int row = table.getSelectedRow();

        if (row < 0 || row >= shownJobs.size()) {
            return null;
        }

        return shownJobs.get(row);
    }

    private void showDetails() {
        Job job = selectedJob();

        if (job == null) {
            message("Select a job in the table first.");
            return;
        }

        UiStyle.showText(this, "Job details", system.describeJob(job).describe());
    }

    // Administrators can cancel any queued or running job; a reason is required.
    private void cancelSelectedJob() {
        Job job = selectedJob();

        if (job == null) {
            message("Select a job in the table first.");
            return;
        }

        if (!job.getStatus().equals(Scheduler.QUEUED) && !job.getStatus().equals(Scheduler.RUNNING)) {
            message("Only queued or running jobs can be cancelled. Job J" + job.getJobId()
                    + " is " + job.getStatus().toLowerCase() + ".");
            return;
        }

        String charge;

        if (job.getStatus().equals(Scheduler.RUNNING)) {
            int minutes = system.getScheduler().getGpu(job.getAssignedGpuId()).getMinutesRun();
            charge = "It is running on GPU" + job.getAssignedGpuId() + ": student " + job.getStudentId()
                    + " will be charged the " + minutes + " minute(s) it has run.";
        } else {
            charge = "It is waiting in the queue: nothing will be charged.";
        }

        String reason = JOptionPane.showInputDialog(this,
                "Cancel job J" + job.getJobId() + " (" + job.getTitle() + ")?\n" + charge
                + "\n\nReason for the cancellation:",
                "Cancel job", JOptionPane.QUESTION_MESSAGE);

        if (reason == null) {
            return;     // the administrator pressed Cancel
        }

        try {
            message(system.cancelJobAsAdmin(job.getJobId(), reason));
        } catch (AccountException e) {
            warn(e.getMessage());
        } catch (RuntimeException e) {
            warn(e.getMessage());
        }
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    private void warn(String text) {
        JOptionPane.showMessageDialog(this, text, "Cancel job", JOptionPane.WARNING_MESSAGE);
    }
}
