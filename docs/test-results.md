# Test results

Tests run on 22/11/2026 with JDK 21; the code was compiled with `--release 8`.
The GUI walkthrough ran the real window on a virtual display (Xvfb), clicking the buttons and
answering the dialogs, starting from the test scenario (5 students, 22 jobs) that `SystemTests`
also builds.

| Test | Checks | Result |
|------|--------|--------|
| `SystemTests` (Person 4): complete system | 123 | **123 passed, 0 failed** |
| `SchedulerTests` (Person 3): scheduler on its own | 38 | **38 passed, 0 failed** |
| GUI walkthrough (real window, buttons and dialogs) | 27 | **27 passed, 0 failed** |
| Start-up and file errors (empty folder, saved folder, damaged file) | 5 | **5 passed** |

## Requirements and where they are tested

| Requirement from the brief | Tested by |
|---|---|
| Registration validation, no duplicate accounts, no repeated approval | SystemTests: Accounts and approval |
| Approval assigns 100 minutes by default; administrator may adjust | SystemTests: Accounts and approval; GUI: approve S006 by typing its ID |
| Clock set once with the pickers, then only Advance Minute | SystemTests: Simulation clock; GUI: +1 minute |
| Reserve full duration; reject if too long; accept exact match; block at zero | SystemTests: Allowance rules; GUI: S003 Large/Medium/Small |
| Completion releases the reservation and charges the runtime once | SystemTests: completion settles once |
| Queued cancellation: no charge. Running cancellation: elapsed runtime, GPU freed, next job starts | SystemTests: queued/running cancellation; GUI: student and admin cancellations |
| Cancellation time, user and reason recorded; cancelled jobs kept | SystemTests; GUI: J23 and J18 |
| FCFS queue, GPUs assigned by increasing ID | SystemTests: Scheduling; SchedulerTests |
| Predictions with copied GPU times, live assignments unchanged, wait = start - now | SystemTests: example from the brief (waits 5 and 10 min) |
| Waits refresh after submissions, cancellations and clock advances | SystemTests: waits refresh |
| Utilization N/A at time zero, includes time before cancellation | SystemTests: utilization; SchedulerTests |
| Save and restore jobs, remaining runtimes, queues, balances, clock | SystemTests: Storage and restart (incl. 97 random restarts) |
| File errors reported clearly | SystemTests: damaged files; start-up checks below |
| Search students by university ID, jobs by keyword/status; students see only their own jobs | SystemTests: Search; GUI: search "satellite", queue tab |
| Five approved students, pending/rejected requests, about twenty jobs in all states | SystemTests: Test scenario |

## Start-up and file errors

| Check | Result |
|---|---|
| Empty data folder: the program starts with no students, no jobs and the clock not started (no prompt) | Passed |
| Saved folder: the program restores it (22 jobs, 10:03) | Passed |
| Damaged `simulation.txt` (letter in a number): message names the file, line 18 and the reason | Passed |
| "Keep a backup and start again" renames the file to `simulation.txt.damaged-<date>` | Passed |
| After the backup the program starts with the accounts and no jobs | Passed |

## GUI walkthrough

```
  PASS  wrong password shows an error
  PASS  student area opens after login
  PASS  duration confirmation is shown (Large, 30 minutes)
  PASS  Large job rejected with 15 available
  PASS  exact match (Medium = 15) accepted with queue info
  PASS  available is now 0
  PASS  empty form reports the missing title
  PASS  zero balance blocks a Small job
  PASS  queue tab lists J23 as waiting with a predicted start
  PASS  student sees only own jobs (5 rows)
  PASS  student cancels queued J23 with no charge
  PASS  admin area has 5 tabs
  PASS  +1 minute twice moves the clock 2 minutes
  PASS  J16 completed and J19 started at 10:05
  PASS  search 'satellite' finds only J18
  PASS  cancel dialog states the charge
  PASS  admin cancels running J18 (charged 7)
  PASS  grant message shows the new balance
  PASS  non-numeric minutes are rejected
  PASS  grant recorded (S003 allocated 60)
  PASS  selecting a request row no longer fills the Request ID
  PASS  approval message shown
  PASS  S006 approved with 100 minutes and an allowance
  PASS  requests tab title updated to 1 pending
  PASS  Save shows the file locations
  PASS  back on the login page
  PASS  after restart: clock 10:05, J18 cancelled, S006 has allowance, grant kept
GUI driver: passed 27, failed 0
```

## SystemTests output

```
== Accounts and approval ==
  PASS  bad email is rejected
  PASS  short password is rejected
  PASS  the ID admin is reserved
  PASS  duplicate registration is rejected
  PASS  a pending student cannot log in
  PASS  wrong administrator password fails
  PASS  repeated approval is blocked
  PASS  approval gives 100 minutes by default
  PASS  administrator can choose other minutes
  PASS  a rejected request gets no allowance
  PASS  2 pending/approved/rejected counts
  PASS  an approved student can log in
  PASS  a rejected student cannot log in
  PASS  student and job manager share one allowance

== Simulation clock ==
  PASS  no submissions before the clock starts
  PASS  only the administrator can start the clock
  PASS  Advance Minute needs a started clock
  PASS  the clock starts at the chosen minute
  PASS  the clock cannot be set a second time
  PASS  Advance Minute moves one minute
  PASS  submission time comes from the simulation clock

== Allowance rules ==
  PASS  Large job accepted with 50 available
  PASS  the full duration is reserved
  PASS  a job longer than the available time is rejected
  PASS  nothing is reserved for a rejected job
  PASS  an exact match is accepted (15 + 5 = 20 available)
  PASS  available time is now zero
  PASS  zero available blocks new submissions
  PASS  reserved jobs still run to completion at zero balance
  PASS  balance never negative and fully used
  PASS  blank title is rejected
Invalid task size
  PASS  invalid task size is rejected
  PASS  too long title is rejected
  PASS  still running after 14 of 15 minutes
  PASS  completed after 15 minutes
  PASS  reservation released, full runtime used
  PASS  job charged and settled once
  PASS  no second charge later
  PASS  a completed job cannot be completed again by JobManager
  PASS  student cancels own queued job
  PASS  queued cancellation charges nothing
  PASS  its reservation is released
  PASS  it leaves the queue and never gets a GPU
  PASS  cancellation time, user and reason are recorded
  PASS  running jobs are untouched
  PASS  administrator cancels a running job
  PASS  elapsed runtime (7 min) is charged
  PASS  the full reservation is released
  PASS  finish time = cancellation time
  PASS  GPU2 is freed and the next job starts at once
  PASS  the result message mentions the charge
  PASS  student cancels own running job; passed runtime is ignored
  PASS  a student cannot cancel another student's job
  PASS  a cancellation needs a reason
  PASS  a completed job cannot be cancelled
  PASS  a cancelled job cannot be cancelled again
  PASS  a cancelled job cannot restart
  PASS  administrator actions need an administrator login
  PASS  extra time increases the allocation
  PASS  the grant is recorded with time and reason
  PASS  a grant needs a reason
  PASS  a grant needs positive minutes
  PASS  a grant needs an approved student
  PASS  allocation cannot drop below used + reserved
  PASS  allocation change is applied and recorded

== Scheduling and predictions ==
  PASS  first four jobs run on GPU1 to GPU4 in order
  PASS  the fifth job waits with no GPU and no times
  PASS  when GPUs free up, the waiting job takes the lowest GPU
  PASS  GPU history is kept after completion
  PASS  example: all four GPUs busy
  PASS  example: two predictions
  PASS  example: J8 position 1, start 10:25, wait 5
  PASS  example: J8 predicted finish 10:40
  PASS  example: J9 position 2, start 10:30, wait 10
  PASS  predictions do not change live GPU assignments
  PASS  JobInfo shows the same prediction
  PASS  example: J8 starts on GPU1 at 10:25
  PASS  wait drops by the minutes advanced
  PASS  cancelling a job ahead moves J9 up and shortens its wait
  PASS  a new submission joins the end of the queue
  PASS  utilization is N/A at time zero
  PASS  time before cancellation counts, waiting does not

== Storage and restart ==
  PASS  restart restores the same state (jobs, GPUs, queue, balances, clock)
  PASS  restart keeps remaining runtimes
  PASS  restart keeps the queue order
  PASS  restart keeps utilization
  PASS  restart keeps the grant history
  PASS  restart keeps the cancellation details
  PASS  nobody is logged in after a restart
  PASS  saving leaves no temporary file
  PASS  70 minutes after a restart both copies are identical
  PASS  the next job ID continues after a restart
  PASS  | \ and new lines survive a restart
  PASS  a missing file means a fresh system
  PASS  bad header is reported
  PASS  a non-number is reported with its line
  PASS  an unknown status is reported
  PASS  balances that do not match the jobs are reported
  PASS  a GPU that disagrees with the jobs is reported
  PASS  a truncated file is reported
  PASS  a cancelled job without a record is reported
  PASS  a bad time is reported
  PASS  data for an unknown student is reported
  PASS  random restarts (97) restore exactly the same state
  PASS  balances never negative, reservations and charges always add up

== Search ==
  PASS  search by job number
  PASS  search by J-number
  PASS  search by student ID
  PASS  search ignores upper/lower case (PyTorch and PyTorch Geometric)
  PASS  status filter
  PASS  keyword and status together
  PASS  empty search shows every job
  PASS  no match gives an empty list
  PASS  a student only finds their own jobs
  PASS  a student cannot find another student's job by number
  PASS  students by university ID

== Test scenario ==
  PASS  scenario: 5 approved, 2 pending, 1 rejected
  PASS  scenario: 22 jobs in all four states
  PASS  scenario: all four GPUs busy with jobs waiting
  PASS  scenario: cancellations by student and administrator, queued and running
  PASS  scenario: S005 has 0 minutes available
  PASS  scenario: S003 has exactly 15 minutes available
  PASS  scenario: the rules hold
------------------------------------------
Passed: 123   Failed: 0
```

The line `Invalid task size` is printed by `Job` when the test submits the size "Huge" on purpose.

## SchedulerTests output

```
PASS  J101 on GPU1
  PASS  J102 on GPU2
  PASS  J103 on GPU3
  PASS  J104 on GPU4
  PASS  J105 stays queued
  PASS  J105 status Queued
  PASS  4 GPUs busy
  PASS  queue sorted by id: 699 before 700
  PASS  later job with larger id goes last
  PASS  two predictions
  PASS  pos1 is J105
  PASS  J105 predicted start 10:05
  PASS  J105 wait 5 min
  PASS  J105 predicted finish 10:10
  PASS  pos2 is J106
  PASS  J106 predicted start 10:05 (2nd free GPU)
  PASS  J106 wait 5 min
  PASS  clock at 10:05
  PASS  all four completed at 10:05
  PASS  4 completion charges of 5 min
  PASS  queued J105 now running on GPU1
  PASS  J105 started at 10:05
  PASS  queue now empty
  PASS  N/A at time zero
  PASS  busy GPU-minutes == 20
  PASS  elapsed == 5
  PASS  utilisation == 100%
  PASS  cancelRunning returned true
  PASS  J201 cancelled
  PASS  J201 finish time 10:03
  PASS  charged elapsed runtime 3 min
  PASS  GPU1 now runs the waiter J205
  PASS  J205 started at 10:03
  PASS  cancelQueued returned true
  PASS  J305 cancelled
  PASS  queue now empty
  PASS  queued cancel fired, no charge
  PASS  running GPUs untouched
------------------------------------------
Passed: 38   Failed: 0
```
