import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import javax.swing.*;

// The simulated time shown in the page header.
// For the administrator it also holds the date and time pickers, used once to start the
// clock, and the Advance Minute button. Students only see the time.
public class ClockPanel extends JPanel implements ActionListener {
    private static final long serialVersionUID = 1L;

    private GpuSystem system;
    private boolean administrator;

    // The panel shows one of three cards.
    private CardLayout cards = new CardLayout();
    private static final String SETUP = "setup";       // administrator, clock not started
    private static final String RUNNING = "running";   // clock started
    private static final String WAITING = "waiting";   // student, clock not started

    private JSpinner dateSpinner;
    private JSpinner timeSpinner;
    private JButton startButton;
    private JButton advanceButton;

    private JLabel timeLabel = new JLabel();

    public ClockPanel(GpuSystem system, boolean administrator) {
        this.system = system;
        this.administrator = administrator;

        setLayout(cards);
        setOpaque(false);

        add(createSetupCard(), SETUP);
        add(createRunningCard(), RUNNING);
        add(createWaitingCard(), WAITING);

        refresh();
    }

    // Date and time pickers with a Start clock button.
    private JPanel createSetupCard() {
        JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        card.setBackground(UiStyle.CARD);
        card.setBorder(UiStyle.cardBorder(10));

        dateSpinner = createSpinner("dd/MM/yyyy", Calendar.DAY_OF_MONTH, 8);
        timeSpinner = createSpinner("HH:mm", Calendar.MINUTE, 4);
        startButton = UiStyle.button("Start clock", UiStyle.GREEN_BUTTON, this);

        card.add(UiStyle.label("Start date:"));
        card.add(dateSpinner);
        card.add(UiStyle.label("Time:"));
        card.add(timeSpinner);
        card.add(startButton);

        return card;
    }

    // The current simulated time, and the Advance Minute button for the administrator.
    private JPanel createRunningCard() {
        JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        card.setBackground(UiStyle.CARD);
        card.setBorder(UiStyle.cardBorder(10));

        timeLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        timeLabel.setForeground(UiStyle.HEADING);

        card.add(UiStyle.label("Simulated time:"));
        card.add(timeLabel);

        if (administrator) {
            advanceButton = UiStyle.button("+1 minute", this);
            card.add(advanceButton);
        }

        return card;
    }

    private JPanel createWaitingCard() {
        JPanel card = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        card.setBackground(UiStyle.CARD);
        card.setBorder(UiStyle.cardBorder(10));
        card.add(UiStyle.label("Simulated time: not started yet"));
        return card;
    }

    // A date or time picker. The arrows change the part of the value under the cursor.
    private JSpinner createSpinner(String pattern, int step, int columns) {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 9);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        JSpinner spinner = new JSpinner(new SpinnerDateModel(start.getTime(), null, null, step));
        JSpinner.DateEditor editor = new JSpinner.DateEditor(spinner, pattern);
        editor.getTextField().setColumns(columns);
        editor.getTextField().setFont(new Font("SansSerif", Font.PLAIN, 13));
        spinner.setEditor(editor);

        return spinner;
    }

    // Shows the right card and the current time. Called after every change.
    public void refresh() {
        SimulationClock clock = system.getClock();

        if (clock.isInitialised()) {
            timeLabel.setText(clock.formattedNow());
            cards.show(this, RUNNING);
        } else if (administrator) {
            cards.show(this, SETUP);
        } else {
            cards.show(this, WAITING);
        }
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        try {
            if (event.getSource() == startButton) {
                startClock();
            } else if (event.getSource() == advanceButton) {
                system.advanceMinute();
            }
        } catch (AccountException e) {
            warn(e.getMessage());
        } catch (RuntimeException e) {
            warn(e.getMessage());
        }
    }

    // Reads the two pickers, asks for confirmation and starts the clock.
    private void startClock() throws AccountException {
        try {
            dateSpinner.commitEdit();
            timeSpinner.commitEdit();
        } catch (ParseException e) {
            warn("Enter the date as dd/MM/yyyy and the time as HH:mm (24-hour).");
            return;
        }

        Calendar date = Calendar.getInstance();
        date.setTime((Date) dateSpinner.getValue());

        Calendar time = Calendar.getInstance();
        time.setTime((Date) timeSpinner.getValue());

        LocalDateTime start = LocalDateTime.of(date.get(Calendar.YEAR), date.get(Calendar.MONTH) + 1,
                date.get(Calendar.DAY_OF_MONTH), time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE));

        int answer = JOptionPane.showConfirmDialog(this,
                "Start the simulation at " + SimulationClock.format(start) + "?\n\n"
                + "The start cannot be changed afterwards. From then on the clock only\n"
                + "moves when you press +1 minute (Advance Minute).",
                "Start the simulation clock", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            system.initialiseClock(start);
        }
    }

    private void warn(String text) {
        JOptionPane.showMessageDialog(this, text, "Simulation clock", JOptionPane.WARNING_MESSAGE);
    }
}
