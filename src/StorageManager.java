import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;

// Saves and restores the simulation in one text file (data/simulation.txt):
//
//   [CLOCK]              start time, current time, elapsed minutes and busy GPU minutes
//   [GPUS]               the job each GPU is running and for how many minutes
//   [ALLOWANCES]         allocated, used and reserved minutes of every student
//   [ALLOWANCE_HISTORY]  recorded extra-time grants and allocation changes
//   [JOBS]               every job with its status, GPU, times and charge
//   [CANCELLATIONS]      cancellation time, user and reason of every cancelled job
//
// Accounts and registration requests are saved by AccountManager (data/accounts.txt).
//
// Saving writes a temporary file first and then replaces the old file, so a failed save
// never leaves a half-written file. Loading checks every line before anything is
// restored, and reports the file name and line number of the first problem.
public class StorageManager {

    public static final String HEADER = "GPU_SIMULATION_V1";

    private static final String[] SECTIONS = {
        "CLOCK", "GPUS", "ALLOWANCES", "ALLOWANCE_HISTORY", "JOBS", "CANCELLATIONS"
    };

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String NEW_LINE = System.getProperty("line.separator");

    private String filename;

    public StorageManager(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public boolean fileExists() {
        return new File(filename).exists();
    }

    // =====================================================================
    // Saving
    // =====================================================================

    public void save(SimulationClock clock, Scheduler scheduler, JobManager jobs) throws StorageException {
        String text = createText(clock, scheduler, jobs);

        File target = new File(filename);
        File temp = new File(filename + ".tmp");
        Writer writer = null;

        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), UTF8));
            writer.write(text);
            writer.close();
            writer = null;

            replaceFile(temp, target);

        } catch (IOException e) {
            throw new StorageException("Could not save " + target.getPath() + ": " + explain(e));

        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // The first error is already being reported.
                }
            }

            // Remove the temporary file if it was not moved into place.
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    // Replaces the old file with the new one in a single step where the system allows it.
    private void replaceFile(File temp, File target) throws IOException {
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Builds the whole file as text. Text fields are escaped so | and line breaks are safe.
    private String createText(SimulationClock clock, Scheduler scheduler, JobManager jobs) {
        StringBuilder out = new StringBuilder();

        line(out, HEADER);
        line(out, "# GPU Utilization Management Application - simulation data.");
        line(out, "# Saved automatically after every change. Times use dd/MM/yyyy HH:mm.");
        line(out, "# Fields are separated by |. In text fields \\p means |, \\n a new line and \\\\ a backslash.");

        line(out, "[CLOCK]");
        line(out, "# started|startTime|currentTime|elapsedMinutes|busyGpuMinutes");

        if (clock.isInitialised()) {
            line(out, "true|" + SimulationClock.format(clock.getStart()) + "|" + clock.formattedNow()
                    + "|" + clock.getElapsedMinutes() + "|" + scheduler.getTotalBusyGpuMinutes());
        } else {
            line(out, "false|-|-|0|0");
        }

        line(out, "[GPUS]");
        line(out, "# gpuId|runningJobId|minutesRun");

        GPU[] gpus = scheduler.getGpus();

        for (int i = 0; i < gpus.length; i++) {
            if (gpus[i].isBusy()) {
                line(out, gpus[i].getId() + "|" + gpus[i].getCurrentJob().getJobId() + "|" + gpus[i].getMinutesRun());
            } else {
                line(out, gpus[i].getId() + "|-|0");
            }
        }

        ArrayList<TimeAllowance> allowances = jobs.getAllowances();

        line(out, "[ALLOWANCES]");
        line(out, "# studentId|allocatedMinutes|usedMinutes|reservedMinutes");

        for (int i = 0; i < allowances.size(); i++) {
            TimeAllowance allowance = allowances.get(i);

            line(out, escape(allowance.getStudentId()) + "|" + allowance.getAllocatedMinutes()
                    + "|" + allowance.getUsedMinutes() + "|" + allowance.getReservedMinutes());
        }

        line(out, "[ALLOWANCE_HISTORY]");
        line(out, "# studentId|record");

        for (int i = 0; i < allowances.size(); i++) {
            ArrayList<String> history = allowances.get(i).getGrantHistory();

            for (int j = 0; j < history.size(); j++) {
                line(out, escape(allowances.get(i).getStudentId()) + "|" + escape(history.get(j)));
            }
        }

        ArrayList<Job> jobList = jobs.getJobs();

        line(out, "[JOBS]");
        line(out, "# jobId|studentId|title|application|description|taskSize|submissionTime"
                + "|status|gpuId|startTime|finishTime|chargedMinutes|settled");

        for (int i = 0; i < jobList.size(); i++) {
            Job job = jobList.get(i);
            String gpu = (job.getAssignedGpuId() == -1) ? "-" : Integer.toString(job.getAssignedGpuId());

            line(out, job.getJobId()
                    + "|" + escape(job.getStudentId())
                    + "|" + escape(job.getTitle())
                    + "|" + escape(job.getApplicationName())
                    + "|" + escape(job.getDescription())
                    + "|" + escape(job.getTaskSize())
                    + "|" + escape(job.getSubmissionTime())
                    + "|" + escape(job.getStatus())
                    + "|" + gpu
                    + "|" + timeOrDash(job.getStartTime())
                    + "|" + timeOrDash(job.getFinishTime())
                    + "|" + job.getChargedMinutes()
                    + "|" + job.isSettled());
        }

        line(out, "[CANCELLATIONS]");
        line(out, "# jobId|cancellationTime|cancelledBy|reason");

        for (int i = 0; i < jobList.size(); i++) {
            Job job = jobList.get(i);

            if (job.getStatus().equals(Scheduler.CANCELLED)) {
                line(out, job.getJobId() + "|" + escape(job.getCancellationTime())
                        + "|" + escape(job.getCancelledBy()) + "|" + escape(job.getCancellationReason()));
            }
        }

        line(out, "[END]");

        return out.toString();
    }

    private void line(StringBuilder out, String text) {
        out.append(text).append(NEW_LINE);
    }

    private String timeOrDash(String time) {
        return (time == null) ? "-" : escape(time);
    }

    // Replaces the characters that have a special meaning in the file.
    static String escape(String text) {
        if (text == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '\\') {
                result.append("\\\\");
            } else if (c == '|') {
                result.append("\\p");
            } else if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }

    // Reverses escape(). Returns null if the text contains an unknown escape sequence.
    static String unescape(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c != '\\') {
                result.append(c);
                continue;
            }

            if (i + 1 >= text.length()) {
                return null;
            }

            i++;
            char code = text.charAt(i);

            if (code == '\\') {
                result.append('\\');
            } else if (code == 'p') {
                result.append('|');
            } else if (code == 'n') {
                result.append('\n');
            } else if (code == 'r') {
                result.append('\r');
            } else {
                return null;
            }
        }

        return result.toString();
    }

    // =====================================================================
    // Loading
    // =====================================================================

    // One data line of the file: its line number (for error messages) and its fields.
    private static class Row {
        private int lineNumber;
        private String[] fields;

        Row(int lineNumber, String[] fields) {
            this.lineNumber = lineNumber;
            this.fields = fields;
        }
    }

    // Restores the clock, allowances, jobs, queue and GPUs. Nothing is changed unless the
    // whole file is valid. A missing file means nothing has been saved yet.
    public void load(SimulationClock clock, Scheduler scheduler, JobManager jobs) throws StorageException {
        File file = new File(filename);

        if (!file.exists()) {
            return;
        }

        ArrayList<String> lines = readLines(file);

        if (lines.isEmpty() || !lines.get(0).trim().equals(HEADER)) {
            throw error(1, "this is not a simulation data file (the first line must be " + HEADER + ").");
        }

        HashMap<String, ArrayList<Row>> sections = readSections(lines);

        // ----- 1. Clock -----
        ArrayList<Row> clockRows = sections.get("CLOCK");

        if (clockRows.size() != 1) {
            throw new StorageException(filename + ": [CLOCK] must contain exactly one line.");
        }

        Row clockRow = clockRows.get(0);
        checkFieldCount(clockRow, 5);

        boolean started = readBoolean(clockRow, 0);
        LocalDateTime startTime = null;
        LocalDateTime now = null;
        int elapsed = 0;
        int busyMinutes = 0;

        if (started) {
            startTime = readTime(clockRow, 1);
            now = readTime(clockRow, 2);
            elapsed = readNumber(clockRow, 3);
            busyMinutes = readNumber(clockRow, 4);

            if (ChronoUnit.MINUTES.between(startTime, now) != elapsed) {
                throw error(clockRow, "the elapsed minutes do not match the start and current times.");
            }

            if (busyMinutes > Scheduler.GPU_COUNT * elapsed) {
                throw error(clockRow, "busy GPU minutes cannot be more than 4 x the elapsed minutes.");
            }
        } else if (readNumber(clockRow, 4) != 0) {
            throw error(clockRow, "busy GPU minutes must be 0 before the clock is started.");
        }

        // ----- 2. Allowances -----
        ArrayList<TimeAllowance> allowanceList = new ArrayList<TimeAllowance>();
        HashMap<String, TimeAllowance> allowances = new HashMap<String, TimeAllowance>();
        ArrayList<Row> allowanceRows = sections.get("ALLOWANCES");

        for (int i = 0; i < allowanceRows.size(); i++) {
            Row row = allowanceRows.get(i);
            checkFieldCount(row, 4);

            String id = readText(row, 0);
            int allocated = readNumber(row, 1);
            int used = readNumber(row, 2);
            int reserved = readNumber(row, 3);

            if (id.trim().isEmpty()) {
                throw error(row, "the student ID is empty.");
            }

            if (allowances.containsKey(id)) {
                throw error(row, "student " + id + " has two allowances.");
            }

            if ((long) used + reserved > allocated) {
                throw error(row, "used + reserved minutes are more than the allocated minutes of " + id + ".");
            }

            TimeAllowance allowance = new TimeAllowance(id, allocated, used, reserved);
            allowances.put(id, allowance);
            allowanceList.add(allowance);
        }

        // ----- 3. Allowance history -----
        ArrayList<Row> historyRows = sections.get("ALLOWANCE_HISTORY");

        for (int i = 0; i < historyRows.size(); i++) {
            Row row = historyRows.get(i);
            checkFieldCount(row, 2);

            TimeAllowance allowance = allowances.get(readText(row, 0));

            if (allowance == null) {
                throw error(row, "history for a student with no allowance: " + readText(row, 0) + ".");
            }

            allowance.getGrantHistory().add(readText(row, 1));
        }

        // ----- 4. Cancellation records (applied to the jobs below) -----
        HashMap<Integer, Row> cancellations = new HashMap<Integer, Row>();
        ArrayList<Row> cancellationRows = sections.get("CANCELLATIONS");

        for (int i = 0; i < cancellationRows.size(); i++) {
            Row row = cancellationRows.get(i);
            checkFieldCount(row, 4);

            int jobId = readPositive(row, 0);

            if (cancellations.containsKey(jobId)) {
                throw error(row, "job " + jobId + " has two cancellation records.");
            }

            cancellations.put(jobId, row);
        }

        // ----- 5. GPUs -----
        Row[] gpuRows = new Row[Scheduler.GPU_COUNT + 1];
        ArrayList<Row> gpuList = sections.get("GPUS");

        for (int i = 0; i < gpuList.size(); i++) {
            Row row = gpuList.get(i);
            checkFieldCount(row, 3);

            int gpuId = readNumber(row, 0);

            if (gpuId < 1 || gpuId > Scheduler.GPU_COUNT) {
                throw error(row, "GPU IDs must be 1 to " + Scheduler.GPU_COUNT + ".");
            }

            if (gpuRows[gpuId] != null) {
                throw error(row, "GPU" + gpuId + " appears twice.");
            }

            if (row.fields[1].equals("-")) {
                if (readNumber(row, 2) != 0) {
                    throw error(row, "a free GPU must have 0 minutes run.");
                }
            } else {
                readPositive(row, 1);
                readNumber(row, 2);
            }

            gpuRows[gpuId] = row;
        }

        for (int gpuId = 1; gpuId <= Scheduler.GPU_COUNT; gpuId++) {
            if (gpuRows[gpuId] == null) {
                throw new StorageException(filename + ": GPU" + gpuId + " is missing from [GPUS].");
            }
        }

        // ----- 6. Jobs: create each job and replay its state changes -----
        ArrayList<Job> loadedJobs = new ArrayList<Job>();
        HashMap<Integer, Integer> minutesRun = new HashMap<Integer, Integer>();
        ArrayList<Row> jobRows = sections.get("JOBS");
        int previousId = 0;

        for (int i = 0; i < jobRows.size(); i++) {
            Row row = jobRows.get(i);
            checkFieldCount(row, 13);

            int id = readPositive(row, 0);

            if (id <= previousId) {
                throw error(row, "jobs must be saved in increasing ID order (job " + id + ").");
            }

            previousId = id;

            if (!started) {
                throw error(row, "a job cannot exist before the clock has been started.");
            }

            Job job = readJob(row, id, allowances, cancellations, gpuRows, now, minutesRun);
            loadedJobs.add(job);
        }

        if (!cancellations.isEmpty()) {
            Row extra = cancellations.values().iterator().next();
            throw error(extra, "cancellation record for job " + extra.fields[0]
                    + ", which is not a cancelled job.");
        }

        // Every busy GPU line must point to a job that is running on that GPU.
        for (int gpuId = 1; gpuId <= Scheduler.GPU_COUNT; gpuId++) {
            Row row = gpuRows[gpuId];

            if (!row.fields[1].equals("-")) {
                int jobId = Integer.parseInt(row.fields[1]);
                Job job = findIn(loadedJobs, jobId);

                if (job == null || !job.getStatus().equals(Scheduler.RUNNING) || job.getAssignedGpuId() != gpuId) {
                    throw error(row, "GPU" + gpuId + " refers to job " + jobId + ", which is not running on it.");
                }
            }
        }

        // ----- 7. Balances must match the jobs -----
        for (int i = 0; i < allowanceList.size(); i++) {
            TimeAllowance allowance = allowanceList.get(i);
            int reserved = 0;
            int used = 0;

            for (int j = 0; j < loadedJobs.size(); j++) {
                Job job = loadedJobs.get(j);

                if (!job.getStudentId().equals(allowance.getStudentId())) {
                    continue;
                }

                if (job.isSettled()) {
                    used += job.getChargedMinutes();
                } else {
                    reserved += job.getEstimatedDuration();
                }
            }

            if (used != allowance.getUsedMinutes() || reserved != allowance.getReservedMinutes()) {
                throw new StorageException(filename + ": the allowance of " + allowance.getStudentId()
                        + " does not match its jobs (saved: used " + allowance.getUsedMinutes()
                        + ", reserved " + allowance.getReservedMinutes() + "; from the jobs: used "
                        + used + ", reserved " + reserved + ").");
            }
        }

        // ----- 8. Busy GPU minutes = total time the jobs have run -----
        int totalRuntime = 0;

        for (int i = 0; i < loadedJobs.size(); i++) {
            Job job = loadedJobs.get(i);

            if (job.getStatus().equals(Scheduler.RUNNING)) {
                totalRuntime += minutesRun.get(job.getJobId());
            } else if (job.getStartTime() != null) {
                totalRuntime += job.getChargedMinutes();     // completed or cancelled while running
            }
        }

        if (started && totalRuntime != busyMinutes) {
            throw error(clockRow, "busy GPU minutes (" + busyMinutes
                    + ") do not match the total runtime of the jobs (" + totalRuntime + ").");
        }

        // ----- 9. Everything is valid: restore it -----
        if (started) {
            clock.restore(startTime, now, elapsed);
        }

        for (int i = 0; i < allowanceList.size(); i++) {
            jobs.addStudentAllowance(allowanceList.get(i));
        }

        for (int i = 0; i < loadedJobs.size(); i++) {
            Job job = loadedJobs.get(i);
            Integer run = minutesRun.get(job.getJobId());

            jobs.addLoadedJob(job);
            scheduler.registerRestoredJob(job, (run == null) ? 0 : run.intValue());
        }

        scheduler.restoreBusyMinutes(busyMinutes);
    }

    // Reads one [JOBS] line, checks it and rebuilds the job by replaying its state changes
    // (start, complete, cancel, settle) with the saved times.
    private Job readJob(Row row, int id, HashMap<String, TimeAllowance> allowances,
            HashMap<Integer, Row> cancellations, Row[] gpuRows, LocalDateTime now,
            HashMap<Integer, Integer> minutesRun) throws StorageException {

        String student = readText(row, 1);
        String title = readRequiredText(row, 2, "title");
        String application = readRequiredText(row, 3, "application name");
        String description = readRequiredText(row, 4, "description");
        String size = readText(row, 5);
        LocalDateTime submitted = readTime(row, 6);
        String status = readText(row, 7);
        LocalDateTime start = readOptionalTime(row, 9);
        LocalDateTime finish = readOptionalTime(row, 10);
        int charged = readNumber(row, 11);
        boolean settled = readBoolean(row, 12);

        if (!allowances.containsKey(student)) {
            throw error(row, "job " + id + " belongs to " + student + ", who has no allowance.");
        }

        if (submitted.isAfter(now)) {
            throw error(row, "job " + id + " was submitted after the current simulation time.");
        }

        Job job = new Job(id, student, title, application, description, size, SimulationClock.format(submitted));
        int duration = job.getEstimatedDuration();

        if (duration == 0) {
            throw error(row, "unknown task size \"" + size + "\".");
        }

        Row cancellation = cancellations.remove(id);

        if (status.equals(Scheduler.QUEUED)) {
            expect(row, row.fields[8].equals("-") && start == null && finish == null,
                    "a queued job has no GPU, start time or finish time.");
            expect(row, charged == 0 && !settled, "a queued job is not settled yet.");
            expect(row, cancellation == null, "job " + id + " is queued but has a cancellation record.");

        } else if (status.equals(Scheduler.RUNNING)) {
            int gpuId = readGpu(row);
            expect(row, start != null && finish == null, "a running job has a start time and no finish time.");
            expect(row, charged == 0 && !settled, "a running job is not settled yet.");
            expect(row, cancellation == null, "job " + id + " is running but has a cancellation record.");
            expect(row, !start.isBefore(submitted) && !start.isAfter(now), "the start time is outside the job's lifetime.");

            Row gpuRow = gpuRows[gpuId];

            if (gpuRow.fields[1].equals("-") || Integer.parseInt(gpuRow.fields[1]) != id) {
                throw error(row, "running job " + id + " is not on GPU" + gpuId + " in [GPUS].");
            }

            int run = Integer.parseInt(gpuRow.fields[2]);

            if (run != ChronoUnit.MINUTES.between(start, now)) {
                throw error(gpuRow, "GPU" + gpuId + " has run job " + id + " for " + run
                        + " minutes, but the job started " + ChronoUnit.MINUTES.between(start, now) + " minutes ago.");
            }

            if (run >= duration) {
                throw error(gpuRow, "job " + id + " should already have been completed.");
            }

            job.startJob(gpuId, SimulationClock.format(start));
            minutesRun.put(id, run);

        } else if (status.equals(Scheduler.COMPLETED)) {
            int gpuId = readGpu(row);
            expect(row, start != null && finish != null, "a completed job has a start and a finish time.");
            expect(row, !start.isBefore(submitted) && !finish.isAfter(now), "the times are outside the job's lifetime.");
            expect(row, ChronoUnit.MINUTES.between(start, finish) == duration, "a completed job runs for its full duration.");
            expect(row, settled && charged == duration, "a completed job is charged its full duration once.");
            expect(row, cancellation == null, "job " + id + " is completed but has a cancellation record.");

            job.startJob(gpuId, SimulationClock.format(start));
            job.completeJob(SimulationClock.format(finish));
            job.settleJob(charged);

        } else if (status.equals(Scheduler.CANCELLED)) {
            if (cancellation == null) {
                throw error(row, "cancelled job " + id + " has no record in [CANCELLATIONS].");
            }

            LocalDateTime cancelledAt = readTime(cancellation, 1);
            String cancelledBy = readText(cancellation, 2);
            String reason = readText(cancellation, 3);

            expect(cancellation, cancelledBy.equalsIgnoreCase("admin") || cancelledBy.equals(student),
                    "a job can only be cancelled by its student or the administrator.");
            expect(cancellation, !reason.trim().isEmpty(), "the cancellation reason is missing.");
            expect(cancellation, !cancelledAt.isBefore(submitted) && !cancelledAt.isAfter(now),
                    "the cancellation time is outside the job's lifetime.");
            expect(row, settled, "a cancelled job is settled.");

            if (start == null) {
                // Cancelled while waiting: no GPU, no runtime and no charge.
                expect(row, row.fields[8].equals("-") && finish == null && charged == 0,
                        "a job cancelled in the queue has no GPU, finish time or charge.");

                job.cancelJob(SimulationClock.format(cancelledAt), cancelledBy, reason);
                job.settleJob(0);
            } else {
                // Cancelled while running: it finished at the cancellation time and pays its runtime.
                int gpuId = readGpu(row);
                expect(row, finish != null && finish.equals(cancelledAt),
                        "a job cancelled while running finishes at its cancellation time.");
                expect(row, !start.isBefore(submitted), "the start time is before the submission time.");

                long ran = ChronoUnit.MINUTES.between(start, finish);
                expect(row, ran >= 0 && ran < duration && charged == ran,
                        "a job cancelled while running is charged exactly the minutes it ran.");

                job.startJob(gpuId, SimulationClock.format(start));
                job.cancelJob(SimulationClock.format(cancelledAt), cancelledBy, reason);
                job.settleJob(charged);
            }

        } else {
            throw error(row, "unknown job status \"" + status + "\".");
        }

        return job;
    }

    // Reads the file as UTF-8 text lines.
    private ArrayList<String> readLines(File file) throws StorageException {
        ArrayList<String> lines = new ArrayList<String>();
        BufferedReader reader = null;

        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            String text = reader.readLine();

            while (text != null) {
                lines.add(text);
                text = reader.readLine();
            }
        } catch (IOException e) {
            throw new StorageException("Could not read " + filename + ": " + explain(e));
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // Reading has finished; nothing else to do.
                }
            }
        }

        // Ignore the byte-order mark that some editors add at the start of the file.
        if (!lines.isEmpty() && lines.get(0).startsWith("﻿")) {
            lines.set(0, lines.get(0).substring(1));
        }

        return lines;
    }

    // Groups the data lines by section. Blank lines and lines starting with # are ignored.
    private HashMap<String, ArrayList<Row>> readSections(ArrayList<String> lines) throws StorageException {
        HashMap<String, ArrayList<Row>> sections = new HashMap<String, ArrayList<Row>>();
        String current = null;
        boolean ended = false;

        for (int i = 1; i < lines.size(); i++) {
            String text = lines.get(i);
            int number = i + 1;

            if (text.trim().isEmpty() || text.startsWith("#")) {
                continue;
            }

            if (ended) {
                throw error(number, "there is data after [END].");
            }

            if (text.startsWith("[")) {
                String name = text.trim();

                if (!name.endsWith("]")) {
                    throw error(number, "bad section heading " + name + ".");
                }

                name = name.substring(1, name.length() - 1);

                if (name.equals("END")) {
                    ended = true;
                    continue;
                }

                if (!isSection(name)) {
                    throw error(number, "unknown section [" + name + "].");
                }

                if (sections.containsKey(name)) {
                    throw error(number, "section [" + name + "] appears twice.");
                }

                sections.put(name, new ArrayList<Row>());
                current = name;

            } else {
                if (current == null) {
                    throw error(number, "data was found before the first section.");
                }

                sections.get(current).add(new Row(number, text.split("\\|", -1)));
            }
        }

        if (!ended) {
            throw new StorageException(filename + ": the file is incomplete ([END] is missing).");
        }

        for (int i = 0; i < SECTIONS.length; i++) {
            if (!sections.containsKey(SECTIONS[i])) {
                throw new StorageException(filename + ": section [" + SECTIONS[i] + "] is missing.");
            }
        }

        return sections;
    }

    private boolean isSection(String name) {
        for (int i = 0; i < SECTIONS.length; i++) {
            if (SECTIONS[i].equals(name)) {
                return true;
            }
        }

        return false;
    }

    private Job findIn(ArrayList<Job> list, int jobId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getJobId() == jobId) {
                return list.get(i);
            }
        }

        return null;
    }

    // ----- field readers: each one reports the line number when the value is wrong -----

    private void checkFieldCount(Row row, int count) throws StorageException {
        if (row.fields.length != count) {
            throw error(row, "expected " + count + " fields separated by | but found " + row.fields.length + ".");
        }
    }

    private String readText(Row row, int index) throws StorageException {
        String text = unescape(row.fields[index]);

        if (text == null) {
            throw error(row, "field " + (index + 1) + " contains an unknown \\ sequence.");
        }

        return text;
    }

    private String readRequiredText(Row row, int index, String name) throws StorageException {
        String text = readText(row, index);

        if (text.trim().isEmpty()) {
            throw error(row, "the " + name + " is empty.");
        }

        return text;
    }

    private int readNumber(Row row, int index) throws StorageException {
        int value;

        try {
            value = Integer.parseInt(row.fields[index].trim());
        } catch (NumberFormatException e) {
            throw error(row, "field " + (index + 1) + " must be a whole number, not \"" + row.fields[index] + "\".");
        }

        if (value < 0) {
            throw error(row, "field " + (index + 1) + " must not be negative.");
        }

        return value;
    }

    private int readPositive(Row row, int index) throws StorageException {
        int value = readNumber(row, index);

        if (value == 0) {
            throw error(row, "field " + (index + 1) + " must be greater than 0.");
        }

        return value;
    }

    private int readGpu(Row row) throws StorageException {
        int gpuId = readNumber(row, 8);

        if (gpuId < 1 || gpuId > Scheduler.GPU_COUNT) {
            throw error(row, "the GPU must be 1 to " + Scheduler.GPU_COUNT + ".");
        }

        return gpuId;
    }

    private boolean readBoolean(Row row, int index) throws StorageException {
        String text = row.fields[index].trim();

        if (text.equals("true")) {
            return true;
        }

        if (text.equals("false")) {
            return false;
        }

        throw error(row, "field " + (index + 1) + " must be true or false.");
    }

    private LocalDateTime readTime(Row row, int index) throws StorageException {
        String text = readText(row, index).trim();

        try {
            return LocalDateTime.parse(text, SimulationClock.FMT);
        } catch (DateTimeParseException e) {
            throw error(row, "\"" + text + "\" is not a date and time in the form dd/MM/yyyy HH:mm.");
        }
    }

    private LocalDateTime readOptionalTime(Row row, int index) throws StorageException {
        if (row.fields[index].trim().equals("-")) {
            return null;
        }

        return readTime(row, index);
    }

    private void expect(Row row, boolean condition, String message) throws StorageException {
        if (!condition) {
            throw error(row, message);
        }
    }

    private StorageException error(Row row, String message) {
        return error(row.lineNumber, message);
    }

    private StorageException error(int lineNumber, String message) {
        return new StorageException(filename + ", line " + lineNumber + ": " + message);
    }

    // A short, readable description of a file error.
    private String explain(IOException e) {
        if (e instanceof AccessDeniedException) {
            return "access was denied (is the file open in another program or read-only?)";
        }

        if (e.getMessage() == null) {
            return e.getClass().getSimpleName();
        }

        return e.getMessage();
    }
}
