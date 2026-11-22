import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableModel;

// The main window (Person 4). Run this class to start the program.
// It extends Person 1's AccountsGUI, so the login, registration and registration-request screens
// are reused. After a login it shows tabs for the administrator or the student, together with
// Person 2's StudentDashboardPanel and Person 3's SchedulerMonitorPanel.
public class MainFrame extends AccountsGUI {
    private static final long serialVersionUID = 1L;

    private static final Color BACKGROUND = new Color(235, 242, 250);
    private static final Color HEADING = new Color(35, 65, 100);
    private static final Color BUTTON = new Color(205, 222, 240);
    private static final Color GREEN = new Color(195, 225, 195);
    private static final Color RED = new Color(240, 200, 200);
    private static final Color BORDER = new Color(190, 205, 220);

    // Every column of a job. Each table shows some of them, chosen by index.
    private static final String[] COLUMNS = {"Job", "Student", "Title", "Application", "Size", "Duration",
        "Status", "GPU", "Position", "Submitted", "Start", "Finish", "Wait", "Runtime", "Charged"};
    private static final int[] ALL_JOBS = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private static final int[] MY_JOBS = {0, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14};
    private static final int[] ACTIVE_JOBS = {0, 1, 3, 6, 5, 8, 10, 11, 12};
    private static final int[] COMPLETED_JOBS = {0, 1, 7, 9, 10, 11, 13, 12, 14};
    private static final int[] STUDENT_JOBS = {0, 2, 4, 6, 7, 10, 11, 14};
    private static final String[] STATUSES = {"All", Scheduler.QUEUED, Scheduler.RUNNING,
        Scheduler.COMPLETED, Scheduler.CANCELLED};
    private static final int REQUESTS_TAB = 3;

    private GpuSystem system;
    private boolean admin;
    private ArrayList<QueuePrediction> predictions = new ArrayList<QueuePrediction>();

    // Header and tabs
    private JTabbedPane tabs;
    private JPanel clockBox;
    private JLabel clockLabel, saveLabel;
    private JSpinner dateSpinner, timeSpinner;
    private JButton startButton, advanceButton;

    // Summary cards and the job search table (used by both areas)
    private JLabel[] stats;
    private JTextField jobSearch;
    private JComboBox<String> statusBox;
    private JTable jobsTable;
    private JLabel jobsCount;

    // Administrator area
    private JLabel[] gpuCards = new JLabel[Scheduler.GPU_COUNT];
    private JTable activeTable, completedTable, allowanceTable, studentsTable, studentJobsTable;
    private JTextField studentSearch, minutesField, reasonField;
    private JLabel studentLabel;
    private JTextArea historyArea;
    private SchedulerMonitorPanel monitor;

    // Student area
    private StudentDashboardPanel studentPanel;
    private JLabel nextJobLabel;

    public MainFrame(GpuSystem system) {
        super(system.getAccountManager(), system);   // the GpuSystem is the JobManager
        this.system = system;

        setTitle("GPU Utilization Management");
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1280, screen.width), Math.min(820, screen.height));
        setLocationRelativeTo(null);

        // Save once more when the window is closed.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                close();
            }
        });
    }

    public static void main(String[] args) {
        final String folder = (args.length > 0) ? args[0] : "data";

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                GpuSystem system = open(folder);

                if (system == null) {
                    System.exit(0);
                }
                new MainFrame(system).setVisible(true);
            }
        });
    }

    // Loads the saved data. If a file is damaged, the problem is shown and the user can keep a
    // backup copy of that file and start again without it. Returns null if the user exits.
    private static GpuSystem open(String folder) {
        while (true) {
            try {
                return new GpuSystem(folder);
            } catch (IOException e) {
                Object[] options = {"Keep a backup and start again", "Exit"};
                int choice = JOptionPane.showOptionDialog(null, "The saved data could not be loaded.\n\n"
                        + e.getMessage() + "\n\nYou can exit and correct the file, or keep a backup copy of it\n"
                        + "and start again without it.", "File error", JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE, null, options, options[1]);

                String name = e.getMessage().startsWith(new File(folder, GpuSystem.ACCOUNTS_FILE).getPath())
                        ? GpuSystem.ACCOUNTS_FILE : GpuSystem.SIMULATION_FILE;
                File damaged = new File(folder, name);
                File backup = new File(folder, name + ".damaged-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));

                if (choice != 0 || !damaged.renameTo(backup)) {
                    return null;
                }
                JOptionPane.showMessageDialog(null, "The damaged file was kept as:\n" + backup.getPath());
            }
        }
    }

    // ----- buttons -----

    // This window's buttons are handled here. All other commands (Login, Register, Approve,
    // Reject, Refresh, Logout, ...) go to AccountsGUI as before.
    @Override
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();
        boolean loggedIn = system.getAccountManager().getCurrentUser() != null;

        try {
            if (command.equals("Start clock")) {
                startClock();
            } else if (command.equals("+1 minute")) {
                system.advanceMinute();
            } else if (command.equals("Save")) {
                system.save();
                inform("All data is saved in the folder:\n" + system.getFolder().getAbsolutePath()
                        + "\n\n(It is also saved automatically after every change.)");
            } else if (command.equals("Show all")) {
                jobSearch.setText("");
                statusBox.setSelectedIndex(0);
                if (studentSearch != null) {
                    studentSearch.setText("");
                }
            } else if (command.equals("View details")) {
                showDetails();
            } else if (command.equals("Cancel selected job")) {
                cancelSelectedJob();
            } else if (command.equals("Grant extra time") || command.equals("Set as new total")) {
                changeAllowance(command.equals("Grant extra time"));
            } else if (!command.equals("Search")) {      // Search only needs the refresh below
                super.actionPerformed(event);

                if (!loggedIn && system.getAccountManager().getCurrentUser() != null) {
                    openArea();
                }
            }
            refresh();

        } catch (NumberFormatException e) {
            inform("Minutes must be a whole number.");
        } catch (Exception e) {
            inform(e.getMessage());
        }
    }

    private void startClock() {
        LocalDateTime date = LocalDateTime.ofInstant(((Date) dateSpinner.getValue()).toInstant(), ZoneId.systemDefault());
        LocalDateTime time = LocalDateTime.ofInstant(((Date) timeSpinner.getValue()).toInstant(), ZoneId.systemDefault());
        LocalDateTime start = date.toLocalDate().atTime(time.getHour(), time.getMinute());

        int answer = JOptionPane.showConfirmDialog(this, "Start the simulation clock at " + SimulationClock.format(start)
                + "?\nIt cannot be changed later: after this only +1 minute moves the time.", "Start clock",
                JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            system.startClock(start);
        }
    }

    private void cancelSelectedJob() {
        Job job = selectedJob();

        if (job == null) {
            return;
        }
        String charge;

        if (job.getStatus().equals(Scheduler.RUNNING)) {
            GPU gpu = system.getScheduler().getGpu(job.getAssignedGpuId());
            charge = "It is running on GPU" + gpu.getId() + ": student " + job.getStudentId()
                    + " will be charged the " + gpu.getMinutesRun() + " minute(s) it has run.";
        } else if (job.getStatus().equals(Scheduler.QUEUED)) {
            charge = "It is waiting in the queue, so nothing will be charged.";
        } else {
            inform("Job J" + job.getJobId() + " is " + job.getStatus().toLowerCase() + " and cannot be cancelled.");
            return;
        }

        String reason = JOptionPane.showInputDialog(this, "Cancel job J" + job.getJobId() + " (" + job.getTitle()
                + ")?\n" + charge + "\n\nReason for the cancellation:");

        if (reason != null) {
            system.cancelJob(job.getJobId(), "admin", reason, null, 0);
            inform(system.getLastMessage());
        }
    }

    private void changeAllowance(boolean grant) {
        String student = selected(studentsTable);
        int minutes = Integer.parseInt(minutesField.getText().trim());
        boolean done;

        if (grant) {
            done = system.grantExtraTime(student, minutes, reasonField.getText(), null);
        } else {
            done = system.changeAllocation(student, minutes, reasonField.getText());
        }
        if (done) {
            minutesField.setText("");
            reasonField.setText("");
        }
        inform(system.getLastMessage());
    }

    private void showDetails() {
        Job job = selectedJob();

        if (job == null) {
            return;
        }
        String[] row = jobRow(job);
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < COLUMNS.length; i++) {
            text.append(COLUMNS[i]).append(": ").append(row[i]).append('\n');
        }
        text.append("Description: ").append(job.getDescription());

        if (job.getCancellationTime() != null) {
            text.append("\nCancelled at ").append(job.getCancellationTime()).append(" by ")
                    .append(job.getCancelledBy()).append("\nReason: ").append(job.getCancellationReason());
        }
        if (job.getStatus().equals(Scheduler.QUEUED)) {
            text.append("\n\nThe start, finish and wait are predictions.");
        }

        JTextArea area = new JTextArea(text.toString(), 18, 45);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area), "Job J" + job.getJobId(), JOptionPane.PLAIN_MESSAGE);
    }

    private Job selectedJob() {
        String id = selected(jobsTable);

        if (id == null) {
            inform("Select a job in the table first.");
            return null;
        }
        return system.findJob(Integer.parseInt(id.substring(1)));
    }

    private void close() {
        try {
            system.save();
        } catch (IOException e) {
            int answer = JOptionPane.showConfirmDialog(this, "The data could not be saved:\n" + e.getMessage()
                    + "\n\nClose anyway?", "Save error", JOptionPane.YES_NO_OPTION);

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        System.exit(0);
    }

    // ----- building the screens after a login -----

    // AccountsGUI has just shown its own page for the user: for the administrator this is the
    // registration requests page, for a student it holds Person 2's StudentDashboardPanel.
    // Both are reused inside the tabs.
    private void openArea() {
        User user = system.getAccountManager().getCurrentUser();
        Container page = getContentPane();
        admin = user instanceof Administrator;
        tabs = new JTabbedPane();
        tabs.setFont(new Font("SansSerif", Font.BOLD, 13));

        if (admin) {
            tabs.addTab("Dashboard", dashboardTab());
            tabs.addTab("All jobs", jobsTab(ALL_JOBS));
            tabs.addTab("Students", studentsTab());
            tabs.addTab("Registration requests", page);

            // Person 3's monitor. Its own clock buttons are hidden: this window has them.
            monitor = new SchedulerMonitorPanel(system.getScheduler());
            ((BorderLayout) monitor.getLayout()).getLayoutComponent(BorderLayout.NORTH).setVisible(false);
            setBackgrounds(monitor);
            tabs.addTab("Scheduler monitor", monitor);
        } else {
            studentPanel = (StudentDashboardPanel) find(page, StudentDashboardPanel.class, null);
            confirmDuration(studentPanel);
            tabs.addTab("Submit and manage jobs", studentPanel);
            tabs.addTab("Queue status and results", queueTab());
        }

        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                refresh();
            }
        });

        JPanel window = panel(new BorderLayout(10, 10));
        window.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        window.add(header(user), BorderLayout.NORTH);
        window.add(tabs, BorderLayout.CENTER);
        setContentPane(window);
    }

    private JPanel header(User user) {
        JPanel names = panel(null);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        names.add(label(admin ? "GPU Utilization Dashboard" : "Student Dashboard", 22));
        names.add(label(admin ? "Administrator" : user.getName() + " (" + user.getUsername() + ")", 14));
        saveLabel = label(" ", 12);
        saveLabel.setForeground(new Color(170, 30, 30));
        names.add(saveLabel);

        // Date and time pickers set the clock once; after that only +1 minute moves it.
        Calendar nine = Calendar.getInstance();
        nine.set(Calendar.HOUR_OF_DAY, 9);
        nine.set(Calendar.MINUTE, 0);
        dateSpinner = spinner(nine.getTime(), "dd/MM/yyyy");
        timeSpinner = spinner(nine.getTime(), "HH:mm");
        startButton = button("Start clock", GREEN);
        advanceButton = button("+1 minute", BUTTON);
        clockLabel = label("", 18);
        clockBox = box(new FlowLayout(FlowLayout.LEFT, 8, 4));

        JPanel right = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(clockBox);
        right.add(button("Save", BUTTON));
        right.add(button("Logout", BUTTON));

        JPanel header = panel(new BorderLayout());
        header.add(names, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private JPanel dashboardTab() {
        JPanel gpus = panel(new GridLayout(1, Scheduler.GPU_COUNT, 10, 0));
        gpus.setBackground(Color.WHITE);

        for (int i = 0; i < Scheduler.GPU_COUNT; i++) {
            gpuCards[i] = new JLabel();
            gpuCards[i].setFont(new Font("SansSerif", Font.PLAIN, 12));
            gpus.add(card("GPU" + (i + 1), 15, gpuCards[i]));
        }

        activeTable = table(ACTIVE_JOBS);
        completedTable = table(COMPLETED_JOBS);
        allowanceTable = table("Student", "Name", "Allocated", "Used", "Reserved", "Available");

        JPanel top = panel(new BorderLayout(0, 8));
        top.add(statRow("Running", "Queued", "Completed", "Cancelled", "Busy GPUs", "Utilization"), BorderLayout.NORTH);
        top.add(card("GPU status", 15, gpus), BorderLayout.CENTER);

        JPanel bottom = panel(new GridLayout(1, 2, 10, 0));
        bottom.add(card("Completed jobs", 15, new JScrollPane(completedTable)));
        bottom.add(card("Student allowances (minutes)", 15, new JScrollPane(allowanceTable)));

        JPanel tables = panel(new GridLayout(2, 1, 0, 8));
        tables.add(card("Active jobs (waiting and running)", 15, new JScrollPane(activeTable)));
        tables.add(bottom);

        JPanel tab = panel(new BorderLayout(0, 8));
        tab.add(top, BorderLayout.NORTH);
        tab.add(tables, BorderLayout.CENTER);
        return tab;
    }

    // Search bar, job table and buttons: "All jobs" for the administrator, own jobs for a student.
    private JPanel jobsTab(int[] columns) {
        jobSearch = new JTextField(20);
        jobSearch.setActionCommand("Search");
        jobSearch.addActionListener(this);
        statusBox = new JComboBox<String>(STATUSES);
        jobsTable = table(columns);
        jobsCount = label("", 12);

        JPanel bar = box(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(label(admin ? "Search jobs:" : "Search my jobs:", 12));
        bar.add(jobSearch);
        bar.add(label("Status:", 12));
        bar.add(statusBox);
        bar.add(button("Search", BUTTON));
        bar.add(button("Show all", BUTTON));

        JPanel buttons = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(button("View details", BUTTON));
        if (admin) {
            buttons.add(button("Cancel selected job", RED));
        }
        JPanel bottom = panel(new BorderLayout());
        bottom.add(buttons, BorderLayout.WEST);
        bottom.add(jobsCount, BorderLayout.EAST);

        JPanel tab = panel(new BorderLayout(0, 8));
        tab.add(bar, BorderLayout.NORTH);
        tab.add(new JScrollPane(jobsTable), BorderLayout.CENTER);
        tab.add(bottom, BorderLayout.SOUTH);
        return tab;
    }

    private JPanel studentsTab() {
        studentSearch = new JTextField(14);
        studentSearch.setActionCommand("Search");
        studentSearch.addActionListener(this);
        studentsTable = table("University ID", "Name", "Email", "Phone", "Allocated", "Used", "Reserved", "Available", "Jobs");
        studentJobsTable = table(STUDENT_JOBS);
        studentLabel = label("", 14);
        minutesField = new JTextField(6);
        reasonField = new JTextField(22);
        historyArea = new JTextArea();
        historyArea.setEditable(false);

        studentsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                showStudent();
            }
        });

        JPanel bar = box(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.add(label("Search by university ID:", 12));
        bar.add(studentSearch);
        bar.add(button("Search", BUTTON));
        bar.add(button("Show all", BUTTON));

        JPanel form = box(new GridLayout(0, 1, 0, 4));
        form.setBorder(null);
        JPanel fields = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        fields.setOpaque(false);
        fields.add(label("Minutes:", 12));
        fields.add(minutesField);
        fields.add(label("Reason:", 12));
        fields.add(reasonField);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.setOpaque(false);
        buttons.add(button("Grant extra time", GREEN));
        buttons.add(button("Set as new total", BUTTON));
        form.add(studentLabel);
        form.add(fields);
        form.add(buttons);
        form.add(label("Allowance history (grants and changes):", 12));

        JPanel allowance = box(new BorderLayout(0, 6));
        allowance.add(form, BorderLayout.NORTH);
        allowance.add(new JScrollPane(historyArea), BorderLayout.CENTER);

        JPanel bottom = panel(new GridLayout(1, 2, 10, 0));
        bottom.add(card("Jobs of the selected student", 15, new JScrollPane(studentJobsTable)));
        bottom.add(allowance);

        JPanel center = panel(new GridLayout(2, 1, 0, 8));
        center.add(card("Approved students", 15, new JScrollPane(studentsTable)));
        center.add(bottom);

        JPanel tab = panel(new BorderLayout(0, 8));
        tab.add(bar, BorderLayout.NORTH);
        tab.add(center, BorderLayout.CENTER);
        return tab;
    }

    private JPanel queueTab() {
        nextJobLabel = label(" ", 12);

        JPanel top = panel(new BorderLayout(0, 6));
        top.add(statRow("Waiting", "Running", "Completed", "Cancelled", "Available minutes"), BorderLayout.NORTH);
        top.add(nextJobLabel, BorderLayout.SOUTH);

        JPanel tab = panel(new BorderLayout(0, 8));
        tab.add(top, BorderLayout.NORTH);
        tab.add(jobsTab(MY_JOBS), BorderLayout.CENTER);
        return tab;
    }

    // Person 2's panel submits as soon as "Submit Job" is pressed. The brief asks the student to
    // confirm the duration, so the button's own action now runs only after the student says Yes.
    private void confirmDuration(final StudentDashboardPanel panel) {
        JButton submit = (JButton) find(panel, JButton.class, "Submit Job");
        final JComboBox<?> sizes = (JComboBox<?>) find(panel, JComboBox.class, null);
        final ActionListener[] original = submit.getActionListeners();

        for (ActionListener listener : original) {
            submit.removeActionListener(listener);
        }
        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String size = (String) sizes.getSelectedItem();
                int minutes = new Job(0, "", "", "", "", size, null).getEstimatedDuration();
                boolean confirmed = !system.getClock().isInitialised()
                        || JOptionPane.showConfirmDialog(panel, "Task size: " + size + "\nDuration: " + minutes
                        + " minutes\n\nThe full duration will be reserved from your available minutes.\n"
                        + "Submit this job?", "Confirm duration", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;

                if (confirmed) {
                    for (ActionListener listener : original) {
                        listener.actionPerformed(e);
                    }
                }
            }
        });
    }

    // ----- refreshing -----

    // Re-reads everything from the system. Called after every action and when a tab is opened.
    private void refresh() {
        User user = system.getAccountManager().getCurrentUser();

        if (user == null || tabs == null) {
            return;
        }
        SimulationClock clock = system.getClock();
        predictions = system.getScheduler().predictQueue();

        clockBox.removeAll();
        if (clock.isInitialised()) {
            clockBox.add(label("Simulated time:", 12));
            clockLabel.setText(clock.formattedNow());
            clockBox.add(clockLabel);
            if (admin) {
                clockBox.add(advanceButton);
            }
        } else if (admin) {
            clockBox.add(label("Start date:", 12));
            clockBox.add(dateSpinner);
            clockBox.add(label("Time:", 12));
            clockBox.add(timeSpinner);
            clockBox.add(startButton);
        } else {
            clockBox.add(label("The clock has not been started yet.", 12));
        }
        saveLabel.setText(system.getSaveError() == null ? " " : "Automatic save failed: " + system.getSaveError());

        // The job search table
        ArrayList<Job> jobs = admin ? system.getJobs() : system.getStudentJobs(user.getUsername());
        DefaultTableModel model = (DefaultTableModel) jobsTable.getModel();
        model.setRowCount(0);

        for (Job job : jobs) {
            if (GpuSystem.matches(job, jobSearch.getText(), (String) statusBox.getSelectedItem())) {
                addRow(jobsTable, jobRow(job), admin ? ALL_JOBS : MY_JOBS);
            }
        }
        jobsCount.setText("Showing " + model.getRowCount() + " of " + jobs.size() + " jobs");

        if (admin) {
            refreshAdmin();
        } else {
            refreshStudent(user.getUsername(), jobs);
        }
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    private void refreshAdmin() {
        Scheduler scheduler = system.getScheduler();
        ArrayList<Job> jobs = system.getJobs();
        setStats(count(jobs, Scheduler.RUNNING), count(jobs, Scheduler.QUEUED), count(jobs, Scheduler.COMPLETED),
                count(jobs, Scheduler.CANCELLED), scheduler.busyGpuCount() + " / " + Scheduler.GPU_COUNT,
                scheduler.utilisationText());

        for (GPU gpu : scheduler.getGpus()) {
            Job job = gpu.getCurrentJob();
            gpuCards[gpu.getId() - 1].setText("<html><table cellpadding=1>"
                    + cell("Status:", gpu.isBusy() ? "Busy" : "Free")
                    + cell("Current job:", gpu.isBusy() ? "J" + job.getJobId() : "-")
                    + cell("Student:", gpu.isBusy() ? job.getStudentId() : "-")
                    + cell("Time remaining:", gpu.isBusy() ? gpu.remainingRuntime() + " min" : "-")
                    + cell("Finish:", gpu.isBusy() ? shortTime(SimulationClock.format(gpu.expectedFinish())) : "-")
                    + "</table></html>");
        }

        String selected = selected(studentsTable);
        clear(activeTable, completedTable, allowanceTable, studentsTable);
        for (GPU gpu : scheduler.getGpus()) {
            if (gpu.isBusy()) {
                addRow(activeTable, jobRow(gpu.getCurrentJob()), ACTIVE_JOBS);
            }
        }
        for (QueuePrediction prediction : scheduler.predictQueue()) {
            addRow(activeTable, jobRow(prediction.getJob()), ACTIVE_JOBS);
        }
        for (int i = jobs.size() - 1; i >= 0; i--) {
            if (jobs.get(i).getStatus().equals(Scheduler.COMPLETED)) {
                addRow(completedTable, jobRow(jobs.get(i)), COMPLETED_JOBS);
            }
        }

        // Students: the allowance table shows all of them, the Students tab only the search result.
        try {
            for (String[] student : system.syncStudents()) {
                TimeAllowance a = system.findAllowance(student[0]);
                Object[] minutes = {a.getAllocatedMinutes(), a.getUsedMinutes(), a.getReservedMinutes(), a.getAvailableMinutes()};
                model(allowanceTable).addRow(new Object[] {student[0], student[1], minutes[0], minutes[1], minutes[2], minutes[3]});

                if (student[0].toLowerCase().contains(studentSearch.getText().trim().toLowerCase())) {
                    model(studentsTable).addRow(new Object[] {student[0], student[1], student[2], student[3], minutes[0],
                        minutes[1], minutes[2], minutes[3], system.getStudentJobs(student[0]).size()});

                    if (student[0].equals(selected)) {
                        int row = studentsTable.getRowCount() - 1;
                        studentsTable.setRowSelectionInterval(row, row);
                    }
                }
            }
            int pending = system.requests("Pending").size();
            tabs.setTitleAt(REQUESTS_TAB, "Registration requests" + (pending > 0 ? " (" + pending + " pending)" : ""));
        } catch (AccountException e) {
            inform(e.getMessage());
        }
        showStudent();
        monitor.refresh();
    }

    // Jobs, balance and allowance history of the student selected in the Students tab.
    private void showStudent() {
        String id = selected(studentsTable);
        TimeAllowance allowance = (id == null) ? null : system.findAllowance(id);
        clear(studentJobsTable);

        if (allowance == null) {
            studentLabel.setText("Select a student in the table.");
            historyArea.setText("");
            return;
        }
        studentLabel.setText(id + ":  allocated " + allowance.getAllocatedMinutes() + ",  used "
                + allowance.getUsedMinutes() + ",  reserved " + allowance.getReservedMinutes()
                + ",  available " + allowance.getAvailableMinutes() + " min");

        for (Job job : system.getStudentJobs(id)) {
            addRow(studentJobsTable, jobRow(job), STUDENT_JOBS);
        }
        StringBuilder history = new StringBuilder();
        for (String record : allowance.getGrantHistory()) {
            history.append(record).append('\n');
        }
        historyArea.setText(history.length() == 0 ? "No extra time granted or allocation changes yet." : history.toString());
    }

    private void refreshStudent(String id, ArrayList<Job> jobs) {
        TimeAllowance allowance = system.findAllowance(id);
        setStats(count(jobs, Scheduler.QUEUED), count(jobs, Scheduler.RUNNING), count(jobs, Scheduler.COMPLETED),
                count(jobs, Scheduler.CANCELLED), (allowance == null) ? 0 : allowance.getAvailableMinutes());

        nextJobLabel.setText(" ");
        for (QueuePrediction p : system.getScheduler().predictQueue()) {
            if (p.getJob().getStudentId().equals(id)) {
                nextJobLabel.setText("Your next waiting job J" + p.getJob().getJobId() + " is number " + p.getPosition()
                        + " in the queue: predicted start " + SimulationClock.format(p.getPredictedStart()) + " (in "
                        + p.getRemainingWaitMinutes() + " min), predicted finish "
                        + SimulationClock.format(p.getPredictedFinish()) + ".");
                break;
            }
        }
        studentPanel.setSimulationTime(system.getClock().isInitialised() ? system.getClock().formattedNow() : null);
    }

    // Every column of one job (see COLUMNS). Waiting jobs show their predicted start and finish.
    private String[] jobRow(Job job) {
        QueuePrediction p = GpuSystem.findPrediction(predictions, job);
        String start = job.getStartTime();
        String finish = job.getFinishTime();
        String wait = minutes(job.getSubmissionTime(), start);
        String runtime = minutes(start, finish);

        if (p != null) {
            start = SimulationClock.format(p.getPredictedStart());
            finish = SimulationClock.format(p.getPredictedFinish());
            wait = p.getRemainingWaitMinutes() + " min";
        } else if (job.getStatus().equals(Scheduler.RUNNING)) {
            GPU gpu = system.getScheduler().getGpu(job.getAssignedGpuId());
            finish = SimulationClock.format(gpu.expectedFinish());
            runtime = gpu.getMinutesRun() + " min";
        }
        String[] row = {"J" + job.getJobId(), job.getStudentId(), job.getTitle(), job.getApplicationName(),
            job.getTaskSize(), job.getEstimatedDuration() + " min", job.getStatus(),
            job.getAssignedGpuId() > 0 ? "GPU" + job.getAssignedGpuId() : "-", (p != null) ? "" + p.getPosition() : "-",
            job.getSubmissionTime(), start, finish, wait, runtime, job.isSettled() ? job.getChargedMinutes() + " min" : "-"};

        for (int i = 0; i < row.length; i++) {
            if (row[i] == null) {
                row[i] = "-";
            }
        }
        return row;
    }

    // ----- small helpers -----

    private static String minutes(String from, String to) {
        int minutes = GpuSystem.minutesBetween(from, to);
        return (minutes < 0) ? "-" : minutes + " min";
    }

    // Times in the tables: only HH:mm on the current simulated day, otherwise dd/MM HH:mm.
    private String shortTime(String time) {
        if (time.length() != 16) {
            return time;
        }
        return time.startsWith(system.getClock().formattedNow().substring(0, 10))
                ? time.substring(11) : time.substring(0, 5) + time.substring(10);
    }

    private static int count(ArrayList<Job> jobs, String status) {
        int count = 0;
        for (Job job : jobs) {
            if (job.getStatus().equals(status)) {
                count++;
            }
        }
        return count;
    }

    private static String cell(String name, String value) {
        return "<tr><td>" + name + "</td><td><b>" + value + "</b></td></tr>";
    }

    private void inform(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    private void setBackgrounds(Component component) {
        if (component instanceof JPanel) {
            component.setBackground(BACKGROUND);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setBackgrounds(child);
            }
        }
    }

    // Finds a component of a type (and, for buttons, with a text) inside another component.
    private static Component find(Component component, Class<?> type, String text) {
        if (type.isInstance(component) && (text == null || text.equals(((AbstractButton) component).getText()))) {
            return component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                Component found = find(child, type, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JPanel statRow(String... titles) {
        JPanel row = panel(new GridLayout(1, titles.length, 10, 0));
        stats = new JLabel[titles.length];

        for (int i = 0; i < titles.length; i++) {
            stats[i] = label("0", 22);
            row.add(card(titles[i], 12, stats[i]));
        }
        return row;
    }

    private void setStats(Object... values) {
        for (int i = 0; i < values.length; i++) {
            stats[i].setText(String.valueOf(values[i]));
        }
    }

    private JTable table(int[] columns) {
        String[] names = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            names[i] = COLUMNS[columns[i]];
        }
        return table(names);
    }

    private JTable table(String... columns) {
        JTable table = new JTable(new DefaultTableModel(columns, 0));
        table.setDefaultEditor(Object.class, null);     // read only
        table.setRowHeight(24);
        table.setFont(new Font("SansSerif", Font.PLAIN, 12));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setReorderingAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setGridColor(new Color(210, 220, 230));
        return table;
    }

    private static DefaultTableModel model(JTable table) {
        return (DefaultTableModel) table.getModel();
    }

    private static void clear(JTable... tables) {
        for (JTable table : tables) {
            model(table).setRowCount(0);
        }
    }

    // Adds the chosen columns of a job row; times are shortened.
    private void addRow(JTable table, String[] row, int[] columns) {
        Object[] values = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            values[i] = (columns[i] >= 9 && columns[i] <= 11) ? shortTime(row[columns[i]]) : row[columns[i]];
        }
        model(table).addRow(values);
    }

    private static String selected(JTable table) {
        int row = table.getSelectedRow();
        return (row < 0) ? null : String.valueOf(table.getValueAt(row, 0));
    }

    private JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(BACKGROUND);
        return panel;
    }

    private JPanel box(LayoutManager layout) {
        JPanel box = new JPanel(layout);
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        return box;
    }

    private JPanel card(String title, int size, Component content) {
        JPanel card = box(new BorderLayout(6, 6));
        card.add(label(title, size), BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    private static JLabel label(String text, int size) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, size));
        label.setForeground(HEADING);
        return label;
    }

    private JButton button(String text, Color colour) {
        JButton button = new JButton(text);
        button.setBackground(colour);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.addActionListener(this);
        return button;
    }

    private static JSpinner spinner(Date value, String pattern) {
        JSpinner spinner = new JSpinner(new SpinnerDateModel(value, null, null, Calendar.MINUTE));
        spinner.setEditor(new JSpinner.DateEditor(spinner, pattern));
        return spinner;
    }
}
