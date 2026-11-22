import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;
import javax.swing.*;
import javax.swing.table.*;

public class StudentDashboardPanel extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    // Matches the colours used by AccountsGUI.
    private final Color background = new Color(235, 242, 250);
    private final Color headingColor = new Color(35, 65, 100);
    private final Color buttonColor = new Color(205, 222, 240);
    private final Color cardColor = Color.WHITE;

    private String studentId;
    private String simulationTime;

    private JobManager manager;

    // Allowance labels
    private JLabel allocatedLabel;
    private JLabel usedLabel;
    private JLabel reservedLabel;
    private JLabel availableLabel;

    // Job input components
    private JTextField titleField;
    private JTextField applicationField;
    private JTextArea descriptionArea;
    private JComboBox<String> taskSizeBox;
    private JLabel durationLabel;

    // Buttons
    private JButton submitButton;
    private JButton cancelButton;
    private JButton detailsButton;
    private JButton searchButton;
    private JButton showAllButton;

    // Search component
    private JTextField searchField;

    // Jobs table
    private JTable jobsTable;
    private DefaultTableModel tableModel;

    public StudentDashboardPanel(String studentId, JobManager manager) {

        this.studentId = studentId;
        this.manager = manager;
        simulationTime = null;

        setLayout(new BorderLayout(15, 15));
        setBackground(background);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        createAllowancePanel();
        createJobFormPanel();
        createJobsTable();
        createButtonPanel();

        refreshAllowance();
        refreshJobs(manager.getStudentJobs(studentId));
    }

    private void createAllowancePanel() {

        JPanel allowancePanel = new JPanel(new GridLayout(1, 4, 12, 0));
        allowancePanel.setBackground(background);

        allocatedLabel = allowanceLabel("Allocated: 0");
        usedLabel = allowanceLabel("Used: 0");
        reservedLabel = allowanceLabel("Reserved: 0");
        availableLabel = allowanceLabel("Available: 0");

        allowancePanel.add(allocatedLabel);
        allowancePanel.add(usedLabel);
        allowancePanel.add(reservedLabel);
        allowancePanel.add(availableLabel);

        add(allowancePanel, BorderLayout.NORTH);
    }

    private void createJobFormPanel() {

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(cardColor);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 205, 220)),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        formPanel.setPreferredSize(new Dimension(390, 0));

        titleField = new JTextField();
        applicationField = new JTextField();
        descriptionArea = new JTextArea(3, 20);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);

        String[] sizes = {"Small", "Medium", "Large", "Very large"};

        taskSizeBox = new JComboBox<String>(sizes);
        taskSizeBox.addActionListener(this);

        durationLabel = new JLabel("5 minutes");

        GridBagConstraints label = new GridBagConstraints();
        label.gridx = 0;
        label.anchor = GridBagConstraints.WEST;
        label.insets = new Insets(7, 0, 7, 12);

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(7, 0, 7, 0);

        addFormRow(formPanel, label, field, 0, "Job title:", titleField);
        addFormRow(formPanel, label, field, 1, "Application name:", applicationField);
        addFormRow(formPanel, label, field, 2, "Description:", new JScrollPane(descriptionArea));
        addFormRow(formPanel, label, field, 3, "Task size:", taskSizeBox);
        addFormRow(formPanel, label, field, 4, "Duration:", durationLabel);

        GridBagConstraints spacer = new GridBagConstraints();
        spacer.gridx = 0;
        spacer.gridy = 5;
        spacer.gridwidth = 2;
        spacer.weighty = 1;
        formPanel.add(Box.createVerticalGlue(), spacer);

        add(formPanel, BorderLayout.WEST);
    }

    private void createJobsTable() {

        String[] columns = {
                "Job ID",
                "Title",
                "Application",
                "Size",
                "Duration",
                "Status",
                "GPU"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        jobsTable = new JTable(tableModel);
        jobsTable.setRowHeight(25);
        jobsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        jobsTable.setGridColor(new Color(210, 220, 230));
        jobsTable.getTableHeader().setBackground(headingColor);
        jobsTable.getTableHeader().setForeground(Color.WHITE);
        jobsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        JScrollPane tableScroll = new JScrollPane(jobsTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(190, 205, 220)));

        add(tableScroll, BorderLayout.CENTER);
    }

    private void createButtonPanel() {

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        buttonPanel.setBackground(background);

        submitButton = new JButton("Submit Job");
        cancelButton = new JButton("Cancel Job");
        detailsButton = new JButton("View Details");

        searchField = new JTextField(12);

        searchButton = new JButton("Search");
        showAllButton = new JButton("Show All");

        styleButton(submitButton);
        styleButton(cancelButton);
        styleButton(detailsButton);
        styleButton(searchButton);
        styleButton(showAllButton);

        submitButton.addActionListener(this);
        cancelButton.addActionListener(this);
        detailsButton.addActionListener(this);
        searchButton.addActionListener(this);
        showAllButton.addActionListener(this);

        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(detailsButton);
        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        searchLabel.setForeground(headingColor);
        buttonPanel.add(searchLabel);
        buttonPanel.add(searchField);
        buttonPanel.add(searchButton);
        buttonPanel.add(showAllButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private JLabel allowanceLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(cardColor);
        label.setForeground(headingColor);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(190, 205, 220)),
                BorderFactory.createEmptyBorder(12, 8, 12, 8)
        ));
        return label;
    }

    private void addFormRow(JPanel panel, GridBagConstraints labelConstraints,
            GridBagConstraints fieldConstraints, int row, String text, Component component) {

        JLabel formLabel = new JLabel(text);
        formLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        formLabel.setForeground(headingColor);

        labelConstraints.gridy = row;
        fieldConstraints.gridy = row;

        panel.add(formLabel, labelConstraints);
        panel.add(component, fieldConstraints);
    }

    private void styleButton(JButton button) {
        button.setBackground(buttonColor);
        button.setForeground(Color.BLACK);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(150, 170, 190)),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)
        ));
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (e.getSource() == taskSizeBox) {
            updateDuration();
        }
        else if (e.getSource() == submitButton) {
            submitJob();
        }
        else if (e.getSource() == cancelButton) {
            cancelSelectedJob();
        }
        else if (e.getSource() == detailsButton) {
            showSelectedJobDetails();
        }
        else if (e.getSource() == searchButton) {
            searchJobs();
        }
        else if (e.getSource() == showAllButton) {
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void updateDuration() {

        String size = (String) taskSizeBox.getSelectedItem();

        if (size.equals("Small")) {
            durationLabel.setText("5 minutes");
        }
        else if (size.equals("Medium")) {
            durationLabel.setText("15 minutes");
        }
        else if (size.equals("Large")) {
            durationLabel.setText("30 minutes");
        }
        else {
            durationLabel.setText("60 minutes");
        }
    }

    private void submitJob() {

        if (simulationTime == null) {
            JOptionPane.showMessageDialog(this, "The simulation clock has not been initialized.");
            return;
        }

        String title = titleField.getText();
        String application = applicationField.getText();
        String description = descriptionArea.getText();
        String taskSize = (String) taskSizeBox.getSelectedItem();

        Job job = manager.submitJob(studentId, title, application, description, taskSize, simulationTime);

        JOptionPane.showMessageDialog(this, manager.getLastMessage());

        if (job != null) {
            clearForm();
            refreshAllowance();
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void cancelSelectedJob() {

        int selectedRow = jobsTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a job from the table.");
            return;
        }

        if (simulationTime == null) {
            JOptionPane.showMessageDialog(this, "The simulation clock has not been initialized.");
            return;
        }

        int jobId = (int) tableModel.getValueAt(selectedRow, 0);

        Job job = manager.findJob(jobId);

        if (job == null) {
            JOptionPane.showMessageDialog(this, "Job not found.");
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Enter the cancellation reason:");

        if (reason == null || reason.trim().equals("")) {
            JOptionPane.showMessageDialog(this, "A cancellation reason is required.");
            return;
        }

        int elapsedRuntime = 0;

        if (job.getStatus().equals("Running")) {
            elapsedRuntime = calculateElapsedRuntime(job);

            if (elapsedRuntime == -1) {
                JOptionPane.showMessageDialog(this, "The elapsed runtime could not be calculated.");
                return;
            }
        }

        boolean cancelled = manager.cancelJob(jobId, studentId, reason, simulationTime, elapsedRuntime);

        JOptionPane.showMessageDialog(this, manager.getLastMessage());

        if (cancelled) {
            refreshAllowance();
            refreshJobs(manager.getStudentJobs(studentId));
        }
    }

    private void showSelectedJobDetails() {

        int selectedRow = jobsTable.getSelectedRow();

        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a job from the table.");
            return;
        }

        int jobId = (int) tableModel.getValueAt(selectedRow, 0);

        Job job = manager.findJob(jobId);

        if (job == null) {
            JOptionPane.showMessageDialog(this, "Job not found.");
            return;
        }

        String gpu;

        if (job.getAssignedGpuId() == -1) {
            gpu = "Not assigned";
        }
        else {
            gpu = Integer.toString(job.getAssignedGpuId());
        }

        String startTime;

        if (job.getStartTime() == null) {
            startTime = "-";
        }
        else {
            startTime = job.getStartTime();
        }

        String finishTime;

        if (job.getFinishTime() == null) {
            finishTime = "-";
        }
        else {
            finishTime = job.getFinishTime();
        }

        String cancellationReason;

        if (job.getCancellationReason() == null) {
            cancellationReason = "-";
        }
        else {
            cancellationReason = job.getCancellationReason();
        }

        String details = "Job ID: " + job.getJobId()
                + "\nStudent ID: " + job.getStudentId()
                + "\nTitle: " + job.getTitle()
                + "\nApplication: " + job.getApplicationName()
                + "\nDescription: " + job.getDescription()
                + "\nTask size: " + job.getTaskSize()
                + "\nDuration: " + job.getEstimatedDuration() + " minutes"
                + "\nSubmission time: " + job.getSubmissionTime()
                + "\nStatus: " + job.getStatus()
                + "\nGPU: " + gpu
                + "\nStart time: " + startTime
                + "\nFinish time: " + finishTime
                + "\nCharged minutes: " + job.getChargedMinutes()
                + "\nCancellation reason: " + cancellationReason;

        JOptionPane.showMessageDialog(this, details, "Job Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private int calculateElapsedRuntime(Job job) {

        try {
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");

            Date start = format.parse(job.getStartTime());
            Date current = format.parse(simulationTime);

            long difference = current.getTime() - start.getTime();

            int minutes = (int) (difference / 60000);

            if (minutes < 0) {
                return -1;
            }

            if (minutes > job.getEstimatedDuration()) {
                minutes = job.getEstimatedDuration();
            }

            return minutes;
        }
        catch (Exception e) {
            return -1;
        }
    }

    private void searchJobs() {

        String keyword = searchField.getText();

        if (keyword.trim().equals("")) {
            refreshJobs(manager.getStudentJobs(studentId));
            return;
        }

        ArrayList<Job> results = manager.searchStudentJobs(studentId, keyword);

        refreshJobs(results);
    }

    private void clearForm() {

        titleField.setText("");
        applicationField.setText("");
        descriptionArea.setText("");
        taskSizeBox.setSelectedIndex(0);
    }

    public void refreshAllowance() {

        TimeAllowance allowance = manager.findAllowance(studentId);

        if (allowance != null) {
            allocatedLabel.setText("Allocated: " + allowance.getAllocatedMinutes());
            usedLabel.setText("Used: " + allowance.getUsedMinutes());
            reservedLabel.setText("Reserved: " + allowance.getReservedMinutes());
            availableLabel.setText("Available: " + allowance.getAvailableMinutes());
        }
    }

    public void refreshJobs(ArrayList<Job> jobs) {

        tableModel.setRowCount(0);

        for (Job job : jobs) {

            Object gpu;

            if (job.getAssignedGpuId() == -1) {
                gpu = "Not assigned";
            }
            else {
                gpu = job.getAssignedGpuId();
            }

            Object[] row = {
                    job.getJobId(),
                    job.getTitle(),
                    job.getApplicationName(),
                    job.getTaskSize(),
                    job.getEstimatedDuration(),
                    job.getStatus(),
                    gpu
            };

            tableModel.addRow(row);
        }
    }

    public void setSimulationTime(String simulationTime) {
        this.simulationTime = simulationTime;
    }
}
