import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;

// "Students" tab: search approved students by university ID, view their balances and jobs,
// and grant extra time or change the allocation, always with a recorded reason.
public class AdminStudentsPanel extends JPanel implements ActionListener, ListSelectionListener {
    private static final long serialVersionUID = 1L;

    private GpuSystem system;

    private JTextField searchField = new JTextField(14);
    private JButton searchButton;
    private JButton showAllButton;

    private DefaultTableModel studentModel = UiStyle.tableModel(new String[] {
        "University ID", "Name", "Email", "Phone", "Allocated", "Used", "Reserved", "Available", "Jobs"
    });
    private JTable studentTable = UiStyle.table(studentModel);
    private ArrayList<Student> shownStudents = new ArrayList<Student>();

    private JLabel jobsTitle = UiStyle.sectionLabel("Jobs");
    private DefaultTableModel jobModel = UiStyle.tableModel(new String[] {
        "Job", "Title", "Size", "Status", "GPU", "Start", "Finish", "Charged"
    });
    private JTable jobTable = UiStyle.table(jobModel);

    private JLabel actionsTitle = UiStyle.sectionLabel("Allowance");
    private JLabel balanceLabel = UiStyle.label(" ");
    private JTextField grantMinutes = new JTextField(5);
    private JTextField grantReason = new JTextField(10);
    private JTextField newAllocation = new JTextField(5);
    private JTextField allocationReason = new JTextField(10);
    private JButton grantButton;
    private JButton allocationButton;
    private DefaultListModel<String> historyModel = new DefaultListModel<String>();

    private String selectedId;          // university ID of the selected student, or null
    private boolean refreshing = false; // ignore selection events while the table is rebuilt

    public AdminStudentsPanel(GpuSystem system) {
        this.system = system;

        setLayout(new BorderLayout(0, 10));
        setBackground(UiStyle.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 2, 2, 2));

        add(createSearchBar(), BorderLayout.NORTH);

        UiStyle.setColumnWidths(studentTable, new int[] {100, 170, 220, 130, 80, 60, 75, 80, 50});
        studentTable.setPreferredScrollableViewportSize(new Dimension(600, 100));
        studentTable.getSelectionModel().addListSelectionListener(this);

        JPanel studentsCard = UiStyle.card("Approved students");
        studentsCard.setBorder(UiStyle.cardBorder(8));
        studentsCard.add(UiStyle.scroll(studentTable), BorderLayout.CENTER);

        // The student list gets 40% of the height, the selected student's details 60%.
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1;

        c.gridy = 0;
        c.weighty = 0.4;
        c.insets = new Insets(0, 0, 10, 0);
        center.add(studentsCard, c);

        c.gridy = 1;
        c.weighty = 0.6;
        c.insets = new Insets(0, 0, 0, 0);
        center.add(createDetailRow(), c);

        add(center, BorderLayout.CENTER);

        refresh();
    }

    private JPanel createSearchBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(UiStyle.CARD);
        bar.setBorder(UiStyle.cardBorder(6));

        searchButton = UiStyle.button("Search", this);
        showAllButton = UiStyle.button("Show all", this);
        searchField.addActionListener(this);

        bar.add(UiStyle.label("Search by university ID:"));
        bar.add(searchField);
        bar.add(searchButton);
        bar.add(showAllButton);

        return bar;
    }

    // The selected student's jobs (left) and the allowance actions (right).
    private JPanel createDetailRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        UiStyle.makeCompact(jobTable);
        UiStyle.setColumnWidths(jobTable, new int[] {42, 170, 70, 78, 46, 118, 118, 60});
        jobTable.setPreferredScrollableViewportSize(new Dimension(500, 100));

        JPanel jobsCard = UiStyle.card(null);
        jobsCard.setBorder(UiStyle.cardBorder(8));
        jobsCard.add(jobsTitle, BorderLayout.NORTH);
        jobsCard.add(UiStyle.scroll(jobTable), BorderLayout.CENTER);

        JPanel actionsCard = createActionsCard();
        actionsCard.setPreferredSize(new Dimension(500, 100));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridy = 0;

        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 5);
        row.add(jobsCard, c);

        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 5, 0, 0);
        row.add(actionsCard, c);

        return row;
    }

    private JPanel createActionsCard() {
        JPanel card = UiStyle.card(null);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        grantButton = UiStyle.button("Grant", UiStyle.GREEN_BUTTON, this);
        allocationButton = UiStyle.button("Set", this);

        // Keep the number fields readable when the window is narrow.
        grantMinutes.setMinimumSize(grantMinutes.getPreferredSize());
        newAllocation.setMinimumSize(newAllocation.getPreferredSize());
        grantReason.setMinimumSize(grantReason.getPreferredSize());
        allocationReason.setMinimumSize(allocationReason.getPreferredSize());

        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 6);

        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 5;
        form.add(actionsTitle, c);

        c.gridy = 1;
        form.add(balanceLabel, c);

        c.gridwidth = 1;
        c.gridy = 2;
        form.add(UiStyle.label("Extra minutes:"), c);
        c.gridx = 1;
        form.add(grantMinutes, c);
        c.gridx = 2;
        form.add(UiStyle.label("Reason:"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(grantReason, c);
        c.gridx = 4;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(grantButton, c);

        c.gridx = 0;
        c.gridy = 3;
        form.add(UiStyle.label("New total:"), c);
        c.gridx = 1;
        form.add(newAllocation, c);
        c.gridx = 2;
        form.add(UiStyle.label("Reason:"), c);
        c.gridx = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        form.add(allocationReason, c);
        c.gridx = 4;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        form.add(allocationButton, c);

        c.gridx = 0;
        c.gridy = 4;
        c.gridwidth = 5;
        c.insets = new Insets(8, 0, 2, 0);
        form.add(UiStyle.label("Allowance history (grants and changes):"), c);

        JList<String> history = new JList<String>(historyModel);
        history.setFont(UiStyle.SMALL_FONT);

        card.add(form, BorderLayout.NORTH);
        card.add(UiStyle.scroll(history), BorderLayout.CENTER);
        return card;
    }

    // Runs the current search again and redraws the selected student's details.
    public void refresh() {
        refreshing = true;

        shownStudents = system.searchStudents(searchField.getText());
        studentModel.setRowCount(0);

        for (int i = 0; i < shownStudents.size(); i++) {
            Student student = shownStudents.get(i);
            TimeAllowance allowance = system.getAllowance(student.getUsername());

            studentModel.addRow(new Object[] {
                student.getUsername(),
                student.getName(),
                student.getEmail(),
                student.getPhone(),
                (allowance == null) ? "-" : Integer.toString(allowance.getAllocatedMinutes()),
                (allowance == null) ? "-" : Integer.toString(allowance.getUsedMinutes()),
                (allowance == null) ? "-" : Integer.toString(allowance.getReservedMinutes()),
                (allowance == null) ? "-" : Integer.toString(allowance.getAvailableMinutes()),
                system.getStudentJobs(student.getUsername()).size()
            });
        }

        // Select the same student again, or the first one found by the search.
        int row = indexOf(selectedId);

        if (row < 0 && !shownStudents.isEmpty()) {
            row = 0;
        }

        if (row >= 0) {
            studentTable.setRowSelectionInterval(row, row);
            selectedId = shownStudents.get(row).getUsername();
        } else {
            selectedId = null;
        }

        refreshing = false;
        showSelectedStudent();
    }

    private int indexOf(String studentId) {
        for (int i = 0; i < shownStudents.size(); i++) {
            if (shownStudents.get(i).getUsername().equals(studentId)) {
                return i;
            }
        }

        return -1;
    }

    // Fills the jobs table, the balance line and the history for the selected student.
    private void showSelectedStudent() {
        jobModel.setRowCount(0);
        historyModel.clear();

        boolean selected = selectedId != null;
        grantButton.setEnabled(selected);
        allocationButton.setEnabled(selected);

        if (!selected) {
            jobsTitle.setText("Jobs (no student selected)");
            actionsTitle.setText("Allowance (no student selected)");
            balanceLabel.setText(" ");
            return;
        }

        Student student = shownStudents.get(indexOf(selectedId));
        TimeAllowance allowance = system.getAllowance(selectedId);

        jobsTitle.setText("Jobs of " + selectedId + " - " + student.getName());
        actionsTitle.setText("Allowance of " + selectedId);

        if (allowance != null) {
            balanceLabel.setText("Allocated " + allowance.getAllocatedMinutes()
                    + "  |  Used " + allowance.getUsedMinutes()
                    + "  |  Reserved " + allowance.getReservedMinutes()
                    + "  |  Available " + allowance.getAvailableMinutes() + " min");

            ArrayList<String> history = allowance.getGrantHistory();

            for (int i = 0; i < history.size(); i++) {
                historyModel.addElement(history.get(i));
            }
        }

        if (historyModel.isEmpty()) {
            historyModel.addElement("No extra time granted or allocation changes yet.");
        }

        ArrayList<JobInfo> jobs = system.describeJobs(system.getStudentJobs(selectedId));

        for (int i = 0; i < jobs.size(); i++) {
            JobInfo info = jobs.get(i);
            Job job = info.getJob();

            jobModel.addRow(new Object[] {
                info.getJobLabel(),
                job.getTitle(),
                job.getTaskSize(),
                job.getStatus(),
                info.getGpuText(),
                info.getStartText(),
                info.getFinishText(),
                info.getChargedText()
            });
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent event) {
        if (refreshing || event.getValueIsAdjusting()) {
            return;
        }

        int row = studentTable.getSelectedRow();

        if (row >= 0 && row < shownStudents.size()) {
            selectedId = shownStudents.get(row).getUsername();
            showSelectedStudent();
        }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        Object source = event.getSource();

        if (source == searchButton || source == searchField) {
            selectedId = null;
            refresh();

            if (shownStudents.isEmpty()) {
                message("No approved student has a university ID containing \"" + searchField.getText().trim() + "\".");
            }
        } else if (source == showAllButton) {
            searchField.setText("");
            refresh();
        } else if (source == grantButton) {
            grantExtraTime();
        } else if (source == allocationButton) {
            changeAllocation();
        }
    }

    private void grantExtraTime() {
        try {
            int minutes = readWholeNumber(grantMinutes, "Extra minutes");
            message(system.grantExtraTime(selectedId, minutes, grantReason.getText()));
            grantMinutes.setText("");
            grantReason.setText("");
        } catch (NumberFormatException e) {
            warn(e.getMessage());
        } catch (AccountException e) {
            warn(e.getMessage());
        } catch (RuntimeException e) {
            warn(e.getMessage());
        }
    }

    private void changeAllocation() {
        try {
            int minutes = readWholeNumber(newAllocation, "The new total");
            message(system.changeAllocation(selectedId, minutes, allocationReason.getText()));
            newAllocation.setText("");
            allocationReason.setText("");
        } catch (NumberFormatException e) {
            warn(e.getMessage());
        } catch (AccountException e) {
            warn(e.getMessage());
        } catch (RuntimeException e) {
            warn(e.getMessage());
        }
    }

    private int readWholeNumber(JTextField field, String name) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new NumberFormatException(name + " must be a whole number of minutes.");
        }
    }

    private void message(String text) {
        JOptionPane.showMessageDialog(this, text);
    }

    private void warn(String text) {
        JOptionPane.showMessageDialog(this, text, "Students", JOptionPane.WARNING_MESSAGE);
    }
}
