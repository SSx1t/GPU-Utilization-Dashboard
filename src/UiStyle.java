import java.awt.*;
import java.awt.event.ActionListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;

// Shared colours, fonts and small factory methods, so that the new screens look like
// the existing AccountsGUI screens: the same light background, navy headings,
// light-blue buttons, white cards and plain tables.
public final class UiStyle {

    // The colours used by AccountsGUI and StudentDashboardPanel.
    public static final Color BACKGROUND = new Color(235, 242, 250);
    public static final Color HEADING = new Color(35, 65, 100);
    public static final Color BUTTON = new Color(205, 222, 240);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(190, 205, 220);
    public static final Color BUTTON_BORDER = new Color(150, 170, 190);
    public static final Color GRID = new Color(210, 220, 230);
    public static final Color GREEN_BUTTON = new Color(195, 225, 195);
    public static final Color RED_BUTTON = new Color(240, 200, 200);
    public static final Color MUTED_TEXT = new Color(95, 110, 130);

    public static final Font TITLE_FONT = new Font("SansSerif", Font.BOLD, 22);
    public static final Font SUBTITLE_FONT = new Font("SansSerif", Font.BOLD, 14);
    public static final Font SECTION_FONT = new Font("SansSerif", Font.BOLD, 15);
    public static final Font LABEL_FONT = new Font("SansSerif", Font.BOLD, 12);
    public static final Font TEXT_FONT = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font SMALL_FONT = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font TABLE_FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 13);
    public static final Font NUMBER_FONT = new Font("SansSerif", Font.BOLD, 22);
    public static final Font COMPACT_TABLE_FONT = new Font("SansSerif", Font.PLAIN, 12);

    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_AND_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private UiStyle() {
        // Only static members.
    }

    // Default colours for tabs and dialogs, set once when the application starts.
    public static void installDefaults() {
        UIManager.put("TabbedPane.selected", CARD);
        UIManager.put("TabbedPane.contentAreaColor", BACKGROUND);
        UIManager.put("TabbedPane.font", new Font("SansSerif", Font.BOLD, 13));
        UIManager.put("OptionPane.background", BACKGROUND);
        UIManager.put("Panel.background", BACKGROUND);
    }

    // ----- buttons and labels -----

    // A light-blue button like the ones on the student dashboard.
    public static JButton button(String text, ActionListener listener) {
        return button(text, BUTTON, listener);
    }

    public static JButton button(String text, Color colour, ActionListener listener) {
        JButton button = new JButton(text);
        button.setBackground(colour);
        button.setForeground(Color.BLACK);
        button.setFont(LABEL_FONT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BUTTON_BORDER),
                BorderFactory.createEmptyBorder(7, 14, 7, 14)));

        if (listener != null) {
            button.addActionListener(listener);
        }

        return button;
    }

    // Bold navy label, as used for the form labels on the student dashboard.
    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setFont(LABEL_FONT);
        label.setForeground(HEADING);
        return label;
    }

    public static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SECTION_FONT);
        label.setForeground(HEADING);
        return label;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(SMALL_FONT);
        label.setForeground(MUTED_TEXT);
        return label;
    }

    // ----- cards -----

    public static Border cardBorder(int padding) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(padding, padding, padding, padding));
    }

    // A white card with a thin border and an optional bold title at the top.
    public static JPanel card(String title) {
        JPanel card = new JPanel(new BorderLayout(8, 8));
        card.setBackground(CARD);
        card.setBorder(cardBorder(12));

        if (title != null) {
            card.add(sectionLabel(title), BorderLayout.NORTH);
        }

        return card;
    }

    // One number of a summary row: a title above a large navy value.
    public static JPanel statCard(String title, JLabel value) {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(LABEL_FONT);
        titleLabel.setForeground(HEADING);

        value.setFont(NUMBER_FONT);
        value.setForeground(HEADING);

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    // ----- tables -----

    // A table model whose cells cannot be edited, like the request table in AccountsGUI.
    public static DefaultTableModel tableModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        styleTable(table);
        return table;
    }

    // The same plain style as the request table in AccountsGUI: default header with bold text.
    public static void styleTable(JTable table) {
        table.setRowHeight(26);
        table.setFont(TABLE_FONT);
        table.setGridColor(GRID);
        table.setBackground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(HEADER_FONT);
    }

    // Slightly smaller rows for wide tables with many columns.
    public static void makeCompact(JTable table) {
        table.setFont(COMPACT_TABLE_FONT);
        table.setRowHeight(24);
        table.getTableHeader().setFont(LABEL_FONT);
    }

    public static void setColumnWidths(JTable table, int[] widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // A scroll pane with the same border colour as the cards.
    public static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(Color.WHITE);
        return pane;
    }

    // ----- other helpers -----

    // Gives every panel inside a component the application background, so a panel that
    // was written without colours (for example SchedulerMonitorPanel) matches the others.
    public static void applyBackground(Component component) {
        if (component instanceof JPanel) {
            component.setBackground(BACKGROUND);
        }

        if (component instanceof Container) {
            Component[] children = ((Container) component).getComponents();

            for (int i = 0; i < children.length; i++) {
                applyBackground(children[i]);
            }
        }
    }

    // Shows long text (for example the details of a job) in a scrollable dialog.
    public static void showText(Component parent, String title, String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("SansSerif", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        area.setCaretPosition(0);

        JScrollPane pane = new JScrollPane(area);
        pane.setPreferredSize(new Dimension(520, 400));
        pane.setBorder(BorderFactory.createLineBorder(BORDER));

        JOptionPane.showMessageDialog(parent, pane, title, JOptionPane.PLAIN_MESSAGE);
    }

    // A compact time for the dashboard: "HH:mm" when it is on the current simulated day,
    // otherwise "dd/MM HH:mm". Returns "-" when there is no time.
    public static String shortTime(LocalDateTime time, SimulationClock clock) {
        if (time == null) {
            return "-";
        }

        if (clock.isInitialised() && time.toLocalDate().equals(clock.now().toLocalDate())) {
            return TIME_ONLY.format(time);
        }

        return DAY_AND_TIME.format(time);
    }

    // The same for a time saved as text (dd/MM/yyyy HH:mm).
    public static String shortTime(String time, SimulationClock clock) {
        if (time == null) {
            return "-";
        }

        try {
            return shortTime(LocalDateTime.parse(time, SimulationClock.FMT), clock);
        } catch (DateTimeParseException e) {
            return time;
        }
    }
}
