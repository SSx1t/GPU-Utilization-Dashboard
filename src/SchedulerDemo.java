import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

// Small window to try the scheduler on its own.
public class SchedulerDemo {

    private static int nextJobId = 101;

    public static void main(String[] args) {
        SimulationClock clock = new SimulationClock();
        Scheduler scheduler = new Scheduler(clock);

        scheduler.setListener(new SchedulerListener() {
            @Override
            public void onJobCompleted(Job job, int runtimeMinutes) {
                System.out.println("[allowance] J" + job.getJobId()
                        + " completed, charge " + runtimeMinutes + " min");
            }

            @Override
            public void onRunningCancelled(Job job, int runtimeMinutes) {
                System.out.println("[allowance] J" + job.getJobId()
                        + " cancelled while running, charge " + runtimeMinutes + " min");
            }

            @Override
            public void onQueuedCancelled(Job job) {
                System.out.println("[allowance] J" + job.getJobId()
                        + " cancelled while queued, no charge");
            }
        });

        SchedulerMonitorPanel panel = new SchedulerMonitorPanel(scheduler);

        // Simple control to add a sample job.
        final String[] sizes = {"Small", "Medium", "Large", "Very large"};
        final JComboBox<String> sizeBox = new JComboBox<String>(sizes);
        final JTextField studentField = new JTextField("S001", 6);
        JButton submit = new JButton("Submit sample job");

        submit.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!clock.isInitialised()) {
                    JOptionPane.showMessageDialog(panel,
                            "Initialise the clock first.", "Demo",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String size = (String) sizeBox.getSelectedItem();
                Job job = new Job(nextJobId++, studentField.getText().trim(),
                        "Sample task", "Model training", "demo job",
                        size, clock.formattedNow());
                scheduler.submit(job);
                panel.refresh();
            }
        });

        JPanel demoBar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        demoBar.add(new JLabel("Student:"));
        demoBar.add(studentField);
        demoBar.add(new JLabel("Size:"));
        demoBar.add(sizeBox);
        demoBar.add(submit);

        JFrame frame = new JFrame("GPU Scheduler Monitor (demo)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.CENTER);
        frame.add(demoBar, BorderLayout.SOUTH);
        frame.setSize(860, 620);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
