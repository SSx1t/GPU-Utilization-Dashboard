# Integration notes (Person 4)

What Person 4 added to combine the four parts, and the small changes made to the
other members' files. Everything else in those files is unchanged.

## New files

| Area | Files |
|------|-------|
| Integration and storage | `GpuSystem`, `IntegratedJobManager`, `SystemChangeListener`, `StorageManager`, `StorageException`, `JobInfo` |
| Main window and screens | `MainApp`, `MainFrame`, `ClockPanel`, `AdminDashboardPanel`, `AdminJobsPanel`, `AdminStudentsPanel`, `StudentQueuePanel`, `UiStyle` |
| Tests | `SystemTests` |
| Documentation | `README.md`, `docs/class-diagram/`, `docs/screenshots/`, `docs/test-results.md` |

## Changes to existing files

| File (owner) | Change | Why |
|---|---|---|
| `AccountsGUI` (Person 1) | Eight members changed from `private` to `protected`: `manager`, `jobManager`, `background`, `headingColor`, `adminPanel`, `showPanel`, `refreshRequests` and `showAccount`. No logic changed and the screens look exactly as before. | `MainFrame` extends `AccountsGUI` to reuse the login, registration and request screens and overrides `showAccount()`. |
| `AccountManager` (Person 1) | Added read-only `getStudents()`, `findStudent(id)` and `getRequests()`. | The dashboards list approved students and count requests; there was no way to read them. |
| `JobManager` (Person 2) | Added `protected setLastMessage(String)`. | `IntegratedJobManager` reports messages such as the queue position after a submission. |
| `StudentDashboardPanel` (Person 2) | Before submitting, a dialog shows the task size and duration and asks for confirmation. | The brief says the student selects a task size and confirms its duration. |
| `SchedulerMonitorPanel` (Person 3) | `%-16s` became `%-16.16s` for the application name. | Long names (for example "Hugging Face Transformers") pushed the waiting-jobs columns out of line. |

`AccountsGUI.main` and `SchedulerDemo` still start the old stand-alone windows; the full
application is `MainApp`.

## How the parts are connected

- `StudentDashboardPanel` receives an `IntegratedJobManager` where it expects a `JobManager`:
  submissions go into the scheduler's FCFS queue and cancellations free the queue place or GPU.
- The scheduler reports completions through `SchedulerListener`; `IntegratedJobManager` settles
  the allowance once.
- After every change `GpuSystem` saves `data/simulation.txt` and tells `MainFrame` (through
  `SystemChangeListener`) to refresh the screens. `AccountManager` still saves `data/accounts.txt`.

## Suggested commits

Each step compiles on its own:

1. Integration and storage: the six integration files plus the `AccountManager` and `JobManager` additions.
2. Main window and screens: the GUI files and the `AccountsGUI`, `StudentDashboardPanel` and `SchedulerMonitorPanel` changes.
3. Tests: `SystemTests` and `docs/test-results.md`.
4. Documentation: `README.md`, `.gitignore`, `docs/class-diagram/`, `docs/screenshots/` and this file.
