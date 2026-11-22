// Implemented by objects that must react when the system data changes.
// IntegratedJobManager tells GpuSystem, which saves the data to the files.
// GpuSystem then tells MainFrame, which refreshes the screens.
public interface SystemChangeListener {

    // change: a short description, for example "Job J12 submitted".
    void systemChanged(String change);
}
