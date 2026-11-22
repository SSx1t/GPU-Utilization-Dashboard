import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

// Starts the GPU Utilization Management Application.
// In Eclipse: right-click MainApp.java > Run As > Java Application.
// From a terminal: java -cp bin MainApp   (optionally followed by another data folder)
//
// The data is kept in the "data" folder of the working directory (the project folder
// when started from Eclipse): accounts.txt and simulation.txt.
public class MainApp {

    public static final String DATA_FOLDER = "data";

    public static void main(String[] args) {
        final String folder = (args.length > 0) ? args[0] : DATA_FOLDER;

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                UiStyle.installDefaults();
                GpuSystem system = openSystem(folder);

                if (system == null) {
                    System.exit(0);
                }

                MainFrame frame = new MainFrame(system);
                frame.setVisible(true);
            }
        });
    }

    // Loads the saved data. The first time (no files yet) the system starts empty.
    // When a file is damaged, explains the problem and offers to keep a backup copy of
    // the file and start without it. Returns null if the user chooses to exit.
    static GpuSystem openSystem(String folderName) {
        File folder = new File(folderName);
        File accounts = new File(folder, GpuSystem.ACCOUNTS_FILE);
        File simulation = new File(folder, GpuSystem.SIMULATION_FILE);

        if (!folder.isDirectory() && !folder.mkdirs()) {
            error("The data folder could not be created:\n" + folder.getAbsolutePath());
            return null;
        }

        while (true) {
            try {
                return new GpuSystem(folderName);

            } catch (StorageException e) {
                if (!offerBackup(simulation, e.getMessage())) {
                    return null;
                }

            } catch (IOException e) {
                if (!offerBackup(accounts, "Could not load " + accounts.getPath() + ": " + e.getMessage())) {
                    return null;
                }

            } catch (AccountException e) {
                if (!offerBackup(accounts, "Could not load " + accounts.getPath() + ": " + e.getMessage())) {
                    return null;
                }
            }
        }
    }

    // Asks whether to rename the damaged file and start again. Returns false to exit.
    private static boolean offerBackup(File file, String problem) {
        Object[] options = {"Keep a backup and start again", "Exit"};

        int choice = JOptionPane.showOptionDialog(null,
                "The saved data could not be loaded.\n\n" + problem + "\n\n"
                + "You can exit and correct the file, or keep a backup copy of it\n"
                + "and start again without it.",
                "File error", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE,
                null, options, options[1]);

        if (choice != 0) {
            return false;
        }

        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File backup = new File(file.getPath() + ".damaged-" + stamp);

        if (!file.exists() || !file.renameTo(backup)) {
            error("The file could not be renamed:\n" + file.getAbsolutePath()
                    + "\nClose any program that is using it and try again.");
            return false;
        }

        JOptionPane.showMessageDialog(null, "The damaged file was kept as:\n" + backup.getAbsolutePath());
        return true;
    }

    private static void error(String text) {
        JOptionPane.showMessageDialog(null, text, "GPU Utilization Management", JOptionPane.ERROR_MESSAGE);
    }
}
