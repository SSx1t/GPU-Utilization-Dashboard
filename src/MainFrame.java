import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

// The main application window.
//
// It extends Person 1's AccountsGUI, so the login, registration and registration-request
// screens are reused as they are, and it overrides showAccount() to open the full
// administrator or student area after login (navigation between the panels):
//
//   Administrator: Dashboard | All jobs | Students | Registration requests | Scheduler monitor
//   Student:       Submit and manage jobs (Person 2) | Queue status and results
//
// It implements SystemChangeListener, so every screen is refreshed after each change
// (submission, cancellation, Advance Minute, approval ...). GpuSystem saves the data first.
public class MainFrame extends AccountsGUI implements SystemChangeListener {
    private static final long serialVersionUID = 1L;

    private static final int REQUESTS_TAB = 3;

    private GpuSystem system;

    // Administrator area (null while no administrator is logged in)
    private JPanel adminArea;
    private JTabbedPane adminTabs;
    private ClockPanel adminClock;
    private AdminDashboardPanel dashboardPanel;
    private AdminJobsPanel jobsPanel;
    private AdminStudentsPanel studentsPanel;
    private SchedulerMonitorPanel monitorPanel;

    // Student area (null while no student is logged in)
    private JPanel studentArea;
    private ClockPanel studentClock;
    private StudentDashboardPanel studentPanel;
    private StudentQueuePanel queuePanel;

    public MainFrame(GpuSystem system) {
        super(system.getAccountManager(), system.getJobManager());
        this.system = system;

        setTitle("GPU Utilization Management Application");

        // 1280 x 840 when the screen has room; on a small or scaled screen (for example a
        // laptop at 150%) the window fits the screen and opens maximised.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1280, screen.width), Math.min(840, screen.height));
        setMinimumSize(new Dimension(Math.min(1000, screen.width), Math.min(640, screen.height)));
        setLocationRelativeTo(null);

        if (screen.width < 1280 || screen.height < 840) {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        // Save before the window closes instead of exiting straight away.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeApplication();
            }
        });

        system.addChangeListener(this);
    }

    // ----- navigation after login (overrides AccountsGUI) -----

    @Override
    protected void showAccount() throws AccountException {
        User user = manager.getCurrentUser();
        system.syncAllowances();
        clearAreas();

        if (user instanceof Administrator) {
            refreshRequests();
            adminArea = createAdminArea();
            showPanel(adminArea);
        } else {
            studentArea = createStudentArea((Student) user);
            showPanel(studentArea);
        }
    }

    private JPanel createAdminArea() {
        adminClock = new ClockPanel(system, true);
        dashboardPanel = new AdminDashboardPanel(system);
        jobsPanel = new AdminJobsPanel(system);
        studentsPanel = new AdminStudentsPanel(system);

        // Person 3's monitor. Its own clock controls are hidden because this window already
        // has the date/time pickers and the Advance Minute button.
        monitorPanel = new SchedulerMonitorPanel(system.getScheduler());
        hideMonitorClockControls(monitorPanel);
        UiStyle.applyBackground(monitorPanel);

        adminTabs = new JTabbedPane();
        adminTabs.addTab("Dashboard", dashboardPanel);
        adminTabs.addTab("All jobs", jobsPanel);
        adminTabs.addTab("Students", studentsPanel);
        adminTabs.addTab(requestsTabTitle(), adminPanel);      // Person 1's request screen
        adminTabs.addTab("Scheduler monitor", monitorPanel);  // Person 3's monitor
        adminTabs.setForeground(UiStyle.HEADING);

        JPanel page = createAreaPage("GPU Utilization Dashboard", "Administrator", adminClock);
        page.add(adminTabs, BorderLayout.CENTER);
        return page;
    }

    private JPanel createStudentArea(Student student) {
        String id = student.getUsername();

        studentClock = new ClockPanel(system, false);
        studentPanel = new StudentDashboardPanel(id, jobManager);    // Person 2's panel
        queuePanel = new StudentQueuePanel(system, id);
        updateStudentClock();

        final JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Submit and manage jobs", studentPanel);
        tabs.addTab("Queue status and results", queuePanel);
        tabs.setForeground(UiStyle.HEADING);

        // Show the latest values whenever the student switches tab.
        tabs.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent event) {
                if (tabs.getSelectedComponent() == queuePanel) {
                    queuePanel.refresh();
                } else if (studentPanel != null) {
                    studentPanel.refreshAllowance();
                }
            }
        });

        JPanel page = createAreaPage("Student Dashboard", student.getName() + " (" + id + ")", studentClock);
        page.add(tabs, BorderLayout.CENTER);
        return page;
    }

    // A page with the header (title, subtitle, clock, Save and Logout) above the tabs.
    private JPanel createAreaPage(String title, String subtitle, ClockPanel clock) {
        JPanel page = new JPanel(new BorderLayout(0, 10));
        page.setBackground(background);
        page.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 0));
        text.setOpaque(false);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UiStyle.TITLE_FONT);
        titleLabel.setForeground(headingColor);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(UiStyle.SUBTITLE_FONT);
        subtitleLabel.setForeground(headingColor);

        text.add(titleLabel);
        text.add(subtitleLabel);

        // The action commands "Save" and "Logout" are handled in actionPerformed().
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        right.add(clock);
        right.add(UiStyle.button("Save", this));
        right.add(UiStyle.button("Logout", this));

        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setOpaque(false);
        header.add(text, BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        page.add(header, BorderLayout.NORTH);
        return page;
    }

    private String requestsTabTitle() {
        int pending = system.countRequests("Pending");

        if (pending == 0) {
            return "Registration requests";
        }

        return "Registration requests (" + pending + " pending)";
    }

    // Person 3's monitor keeps its clock controls in the NORTH part of its BorderLayout.
    private void hideMonitorClockControls(SchedulerMonitorPanel monitor) {
        LayoutManager layout = monitor.getLayout();

        if (layout instanceof BorderLayout) {
            Component top = ((BorderLayout) layout).getLayoutComponent(BorderLayout.NORTH);

            if (top != null) {
                top.setVisible(false);
            }
        }
    }

    private void clearAreas() {
        adminArea = null;
        adminTabs = null;
        adminClock = null;
        dashboardPanel = null;
        jobsPanel = null;
        studentsPanel = null;
        monitorPanel = null;

        studentArea = null;
        studentClock = null;
        studentPanel = null;
        queuePanel = null;
    }

    // ----- buttons -----

    @Override
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();

        if ("Save".equals(command)) {
            saveWithMessage();
            return;
        }

        // Person 1's login, registration, approval, rejection and logout logic.
        super.actionPerformed(event);

        if ("Approve".equals(command) || "Reject".equals(command)) {
            // A newly approved student gets an allowance; everything is saved and redrawn.
            system.accountsChanged();
        } else if ("Logout".equals(command) && manager.getCurrentUser() == null) {
            clearAreas();
        }
    }

    // ----- SystemChangeListener -----

    @Override
    public void systemChanged(String change) {
        refreshScreens();

        String error = system.takeSaveError();

        if (error != null) {
            JOptionPane.showMessageDialog(this, error + "\n\nThe change is kept in memory. Fix the problem "
                    + "(for example close the file in other programs) and press Save.",
                    "Could not save", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Redraws whichever area is showing.
    private void refreshScreens() {
        if (adminArea != null) {
            adminClock.refresh();
            dashboardPanel.refresh();
            jobsPanel.refresh();
            studentsPanel.refresh();
            monitorPanel.refresh();
            adminTabs.setTitleAt(REQUESTS_TAB, requestsTabTitle());
        }

        if (studentArea != null) {
            studentClock.refresh();
            updateStudentClock();
            studentPanel.refreshAllowance();
            queuePanel.refresh();
        }
    }

    // Person 2's panel needs the current simulated time before it accepts a job.
    private void updateStudentClock() {
        if (system.getClock().isInitialised()) {
            studentPanel.setSimulationTime(system.getClock().formattedNow());
        }
    }

    // ----- saving and closing -----

    private void saveWithMessage() {
        try {
            system.save();
            JOptionPane.showMessageDialog(this, "All data is saved in:\n"
                    + system.getAccountsFile().getAbsolutePath() + "\n"
                    + system.getSimulationFile().getAbsolutePath()
                    + "\n\n(The data is also saved automatically after every change.)");
        } catch (StorageException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Could not save", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Saves once more and exits. If saving fails the user can stay and try again.
    private void closeApplication() {
        try {
            system.save();
        } catch (StorageException e) {
            int answer = JOptionPane.showConfirmDialog(this, e.getMessage()
                    + "\n\nClose anyway? Changes since the last successful save will be lost.",
                    "Could not save", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }

        dispose();
        System.exit(0);
    }
}
