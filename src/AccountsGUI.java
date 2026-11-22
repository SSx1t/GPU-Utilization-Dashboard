import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

public class AccountsGUI extends JFrame implements ActionListener {
    private static final long serialVersionUID = 1L;

    private AccountManager manager;
    private JobManager jobManager;
    private StudentDashboardPanel studentDashboard;

    // Basic interface colors.
    private final Color background = new Color(235, 242, 250);
    private final Color headingColor = new Color(35, 65, 100);
    private final Color buttonColor = new Color(205, 222, 240);

    // Login fields.
    private JTextField username = new JTextField();
    private JPasswordField loginPassword = new JPasswordField();

    // Registration fields.
    private JTextField id = new JTextField();
    private JTextField name = new JTextField();
    private JTextField email = new JTextField();
    private JTextField phone = new JTextField();
    private JPasswordField password = new JPasswordField();
    private JPasswordField confirm = new JPasswordField();

    // Administrator approval fields.
    private JTextField requestId = new JTextField(12);
    private JTextField minutes = new JTextField("100", 5);

    // Stores registration requests in a table that cannot be edited directly.
    private DefaultTableModel requestsModel = new DefaultTableModel(new String[]{"University ID", "Name", "Email", "Phone", "Status", "Initial minutes"}, 0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private JTable requests = new JTable(requestsModel);

    private JPanel loginPanel;
    private JPanel registrationPanel;
    private JPanel adminPanel;

    // Creates the window and its panels.
    public AccountsGUI(AccountManager manager, JobManager jobManager) {
        this.manager = manager;
        this.jobManager = jobManager;

        setTitle("GPU Accounts and Registration");
        setSize(1050, 700);
        setMinimumSize(new Dimension(900, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Login form.
        JPanel loginForm = new JPanel(new GridLayout(3, 2, 10, 10));
        loginForm.setBackground(background);
        loginForm.setPreferredSize(new Dimension(430, 140));

        loginForm.add(new JLabel("University ID / admin"));
        loginForm.add(username);

        loginForm.add(new JLabel("Password"));
        loginForm.add(loginPassword);

        loginForm.add(button("Login"));
        loginForm.add(button("Register"));

        loginPanel = createPage("GPU Account Login");

        JPanel loginArea = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 40));
        loginArea.setBackground(background);
        loginArea.add(loginForm);

        loginPanel.add(loginArea, BorderLayout.CENTER);

        // Allow Enter to submit the login form.
        loginPassword.setActionCommand("Login");
        loginPassword.addActionListener(this);

        // Registration form.
        JPanel registrationForm = new JPanel(new GridLayout(7, 2, 10, 10));
        registrationForm.setBackground(background);
        registrationForm.setPreferredSize(new Dimension(500, 300));

        registrationForm.add(new JLabel("University ID"));
        registrationForm.add(id);

        registrationForm.add(new JLabel("Name"));
        registrationForm.add(name);

        registrationForm.add(new JLabel("Email"));
        registrationForm.add(email);

        registrationForm.add(new JLabel("Phone"));
        registrationForm.add(phone);

        registrationForm.add(new JLabel("Password"));
        registrationForm.add(password);

        registrationForm.add(new JLabel("Confirm password"));
        registrationForm.add(confirm);

        registrationForm.add(button("Submit"));
        registrationForm.add(button("Back"));

        registrationPanel = createPage("Student Registration");

        JPanel registrationArea = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 25));
        registrationArea.setBackground(background);
        registrationArea.add(registrationForm);

        registrationPanel.add(registrationArea, BorderLayout.CENTER);

        // Administrator request table.
        adminPanel = createPage("Administrator - Registration Requests");

        requests.setRowHeight(28);
        requests.setFont(new Font("SansSerif", Font.PLAIN, 13));
        requests.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
        requests.getTableHeader().setReorderingAllowed(false);
        requests.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        requests.setFillsViewportHeight(true);
        requests.setBackground(Color.WHITE);
        requests.setGridColor(new Color(210, 220, 230));

        // Give the name and email columns more space.
        requests.getColumnModel().getColumn(0).setPreferredWidth(120);
        requests.getColumnModel().getColumn(1).setPreferredWidth(160);
        requests.getColumnModel().getColumn(2).setPreferredWidth(220);
        requests.getColumnModel().getColumn(3).setPreferredWidth(120);
        requests.getColumnModel().getColumn(4).setPreferredWidth(100);
        requests.getColumnModel().getColumn(5).setPreferredWidth(110);

        adminPanel.add(new JScrollPane(requests), BorderLayout.CENTER);

        // Keep the input fields and buttons on separate rows.
        JPanel approvalFields = new JPanel(new FlowLayout());
        approvalFields.setBackground(background);

        approvalFields.add(new JLabel("Request ID:"));
        approvalFields.add(requestId);
        approvalFields.add(new JLabel("Initial minutes:"));
        approvalFields.add(minutes);

        JPanel approvalButtons = new JPanel(new FlowLayout());
        approvalButtons.setBackground(background);

        approvalButtons.add(button("Approve"));
        approvalButtons.add(button("Reject"));
        approvalButtons.add(button("Refresh"));
        approvalButtons.add(button("Logout"));

        JPanel controls = new JPanel(new GridLayout(2, 1));
        controls.setBackground(background);
        controls.add(approvalFields);
        controls.add(approvalButtons);

        adminPanel.add(controls, BorderLayout.SOUTH);

        showPanel(loginPanel);
    }

    // Creates a page with a heading and space around its contents.
    private JPanel createPage(String title) {
        JPanel panel = new JPanel(new BorderLayout(10, 15));
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(headingColor);

        panel.add(heading, BorderLayout.NORTH);

        return panel;
    }

    // Creates a button and connects it to actionPerformed().
    private JButton button(String text) {
        JButton button = new JButton(text);
        button.addActionListener(this);
        button.setBackground(buttonColor);
        button.setForeground(Color.BLACK);

        if (text.equals("Approve")) {
            button.setBackground(new Color(195, 225, 195));
        } else if (text.equals("Reject")) {
            button.setBackground(new Color(240, 200, 200));
        }

        return button;
    }

    // Changes the displayed panel and refreshes the window.
    private void showPanel(JPanel panel) {
        setContentPane(panel);
        revalidate();
        repaint();
    }

    // Displays a message in a dialog box.
    private void message(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    // Converts the existing pipe-separated request summary into table rows.
    private void refreshRequests() throws AccountException {
        String summary = manager.getRequestSummary();

        requestsModel.setRowCount(0);
        requestId.setText("");

        for (String line : summary.split("\\R")) {
            String[] values = line.split("\\|", -1);

            // Ignore blank lines or messages that are not request records.
            if (values.length != 6) {
                continue;
            }

            for (int i = 0; i < values.length; i++) {
                values[i] = values[i].trim();
            }

            // Remove the label so the column contains only the minutes.
            values[5] = values[5].replace("Initial minutes:", "").trim();

            requestsModel.addRow(values);
        }
    }

    // Displays the appropriate panel after login.
    private void showAccount() throws AccountException {
        User user = manager.getCurrentUser();

        if (user instanceof Administrator) {
            refreshRequests();
            showPanel(adminPanel);
        } else {
            Student student = (Student) user;

            // Do not reset an existing allowance when the student logs in.
            if (jobManager.findAllowance(student.getUsername()) == null) {
                jobManager.addStudentAllowance(student.getTimeAllowance());
            }

            studentDashboard = new StudentDashboardPanel(student.getUsername(), jobManager);

            JPanel panel = createPage("Student Dashboard");
            panel.add(studentDashboard, BorderLayout.CENTER);

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            controls.setBackground(background);
            controls.add(button("Logout"));

            panel.add(controls, BorderLayout.SOUTH);

            showPanel(panel);
        }
    }

    // Handles button clicks and calls the account manager.
    @Override
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();

        try {
            if (command.equals("Login")) {
                String entered = new String(loginPassword.getPassword());
                loginPassword.setText("");

                manager.login(username.getText().trim(), entered);
                showAccount();

            } else if (command.equals("Register")) {
                showPanel(registrationPanel);

            } else if (command.equals("Submit")) {
                String chosen = new String(password.getPassword());
                String confirmed = new String(confirm.getPassword());

                // Reject registration if the passwords differ.
                if (!chosen.equals(confirmed)) {
                    throw new AccountException("Passwords do not match.");
                }

                manager.register(id.getText().trim(), name.getText().trim(), email.getText().trim(), phone.getText().trim(), chosen);

                // Clear the form after successful registration.
                id.setText("");
                name.setText("");
                email.setText("");
                phone.setText("");
                password.setText("");
                confirm.setText("");

                message("Registration saved. Wait for approval.");
                showPanel(loginPanel);

            } else if (command.equals("Back")) {
                password.setText("");
                confirm.setText("");
                showPanel(loginPanel);

            } else if (command.equals("Approve")) {
                int allowance = Integer.parseInt(minutes.getText().trim());

                manager.approve(requestId.getText().trim(), allowance);
                refreshRequests();

                message("Student approved.");

            } else if (command.equals("Reject")) {
                manager.reject(requestId.getText().trim());
                refreshRequests();

                message("Request rejected.");

            } else if (command.equals("Refresh")) {
                refreshRequests();

            } else if (command.equals("Logout")) {
                // End the session without resetting the job manager.
                manager.logout();
                username.setText("");
                showPanel(loginPanel);
            }

        } catch (NumberFormatException e) {
            message("Minutes must be a whole number within the int range.");

        } catch (AccountException e) {
            message(e.getMessage());

        } catch (IOException e) {
            message("File error: " + e.getMessage());
        }
    }

    // Starts the GUI on Swing's event thread.
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new StartGUI());
    }

    // Loads account data and opens the window.
    private static class StartGUI implements Runnable {
        @Override
        public void run() {
            try {
                AccountManager manager = new AccountManager("accounts.txt");
                JobManager jobManager = new JobManager();
                AccountsGUI window = new AccountsGUI(manager, jobManager);
                window.setVisible(true);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Cannot start: " + e.getMessage());
            }
        }
    }
}
