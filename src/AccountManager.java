import java.io.*;
import java.util.ArrayList;
import java.util.Scanner;

public class AccountManager {
    // Stores active accounts and all registration requests.
    private ArrayList<User> users = new ArrayList<User>();
    private ArrayList<AccessRequest> requests = new ArrayList<AccessRequest>();

    private User currentUser;
    private String filename;

    // Creates the administrator account and loads saved data.
    public AccountManager(String filename) throws IOException, AccountException {
        this.filename = filename;
        users.add(new Administrator());
        load();
    }

    // Validates registration details before creating or loading a request.
    private void validate(String id, String name, String email, String phone, String password) throws AccountException {
        String[] fields = {id, name, email, phone, password};

        for (int i = 0; i < fields.length; i++) {
            // Reject missing or blank fields.
            if (fields[i] == null || fields[i].trim().isEmpty()) {
                throw new AccountException("All fields are required.");
            }

            // Reject line breaks because each field is saved on one line.
            if (fields[i].contains("\n")) {
                throw new AccountException("Fields must be on one line.");
            }
        }

        // Prevent students from using the administrator username.
        if (id.equalsIgnoreCase("admin")) {
            throw new AccountException("The username admin is reserved.");
        }

        for (int i = 0; i < id.length(); i++) {
            // Reject characters other than letters and digits.
            if (!Character.isLetterOrDigit(id.charAt(i))) {
                throw new AccountException("ID must contain only letters and digits.");
            }
        }

        int at = email.indexOf('@');
        int dot = email.lastIndexOf('.');

        // Check the positions of @ and the final dot, and reject spaces.
        if (at <= 0 || at != email.lastIndexOf('@') || dot <= at + 1 || dot == email.length() - 1 || email.contains(" ")) {
            throw new AccountException("Enter a valid email address.");
        }

        int digits = 0;

        for (int i = 0; i < phone.length(); i++) {
            char c = phone.charAt(i);

            if (c >= '0' && c <= '9') {
                digits++;
            } else if (c != ' ' && c != '-' && !(c == '+' && i == 0)) {
                // Reject unsupported phone characters.
                throw new AccountException("Phone may contain digits, spaces, - and a leading +.");
            }
        }

        // Count digits only, excluding spaces and other allowed symbols.
        if (digits < 7 || digits > 15) {
            throw new AccountException("Phone must contain 7 to 15 digits.");
        }

        // Reject passwords shorter than the minimum length.
        if (password.length() < 6) {
            throw new AccountException("Password must contain at least 6 characters.");
        }
    }

    // Searches for a request by ID, ignoring uppercase and lowercase differences.
    private AccessRequest findRequest(String id) {
        for (int i = 0; i < requests.size(); i++) {
            AccessRequest request = requests.get(i);

            if (request.getId().equalsIgnoreCase(id)) {
                return request;
            }
        }

        // No matching request was found.
        return null;
    }

    // Validates registration details, prevents duplicates and saves a pending request.
    public void register(String id, String name, String email, String phone, String password) throws AccountException, IOException {
        // Registration is only allowed when nobody is logged in.
        if (currentUser != null) {
            throw new AccountException("Log out before registering.");
        }

        validate(id, name, email, phone, password);

        // Existing requests include pending, approved and rejected registrations.
        if (findRequest(id) != null) {
            throw new AccountException("This ID already has a request or account.");
        }

        AccessRequest request = new AccessRequest(id, name, email, phone, password);
        requests.add(request);

        try {
            save();
        } catch (IOException e) {
            // Undo the addition in memory if saving fails.
            requests.remove(request);
            throw e;
        }
    }

    // Checks login details and remembers the successfully logged-in user.
    public User login(String username, String password) throws AccountException {
        // Prevent another login while a session is active.
        if (currentUser != null) {
            throw new AccountException("Log out first.");
        }

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            if (user.getUsername().equalsIgnoreCase(username) && user.checkPassword(password)) {
                currentUser = user;
                return user;
            }
        }

        // No active account matched the supplied credentials.
        throw new AccountException("Incorrect login or account not approved.");
    }

    // Ends the current session without deleting the account.
    public void logout() {
        currentUser = null;
    }

    // Returns the logged-in user, or null if nobody is logged in.
    public User getCurrentUser() {
        return currentUser;
    }

    // Returns the approved student accounts. The list is a copy, so callers cannot change it.
    public ArrayList<Student> getStudents() {
        ArrayList<Student> students = new ArrayList<Student>();

        for (int i = 0; i < users.size(); i++) {
            if (users.get(i) instanceof Student) {
                students.add((Student) users.get(i));
            }
        }

        return students;
    }

    // Finds an approved student by university ID, ignoring case. Returns null if not found.
    public Student findStudent(String id) {
        ArrayList<Student> students = getStudents();

        for (int i = 0; i < students.size(); i++) {
            if (students.get(i).getUsername().equalsIgnoreCase(id)) {
                return students.get(i);
            }
        }

        return null;
    }

    // Returns all registration requests (pending, approved and rejected) as a copy.
    public ArrayList<AccessRequest> getRequests() {
        return new ArrayList<AccessRequest>(requests);
    }

    // Restricts administrator operations to an administrator session.
    private void requireAdmin() throws AccountException {
        if (!(currentUser instanceof Administrator)) {
            throw new AccountException("Administrator login required.");
        }
    }

    // Builds the registration request summary shown in the administrator panel.
    public String getRequestSummary() throws AccountException {
        requireAdmin();

        String text = "";

        for (int i = 0; i < requests.size(); i++) {
            AccessRequest request = requests.get(i);
            text += request.getId() + " | " + request.getName() + " | " + request.getEmail() + " | " + request.getPhone() + " | " + request.getStatus() + " | Initial minutes: " + request.getMinutes() + "\n";
        }

        if (text.isEmpty()) {
            return "No registration requests.";
        }

        return text;
    }

    // Approves a request with the default allowance of 100 minutes.
    public void approve(String id) throws AccountException, IOException {
        approve(id, 100);
    }

    // Overloaded method that approves a request with a chosen allowance.
    public void approve(String id, int minutes) throws AccountException, IOException {
        decide(id, true, minutes);
    }

    // Rejects a request without granting any GPU minutes.
    public void reject(String id) throws AccountException, IOException {
        decide(id, false, 0);
    }

    // Handles approval or rejection and saves the updated account data.
    private void decide(String id, boolean approve, int minutes) throws AccountException, IOException {
        requireAdmin();

        AccessRequest request = findRequest(id);

        // Block missing requests and requests that have already been decided.
        if (request == null || !request.getStatus().equals("Pending")) {
            throw new AccountException("Enter the ID of a pending request.");
        }

        // An initial allowance cannot be negative.
        if (minutes < 0) {
            throw new AccountException("Minutes cannot be negative.");
        }

        if (approve) {
            request.setDecision("Approved", minutes);
        } else {
            request.setDecision("Rejected", minutes);
        }

        Student student = null;

        // Only approved requests create accounts that can log in.
        if (approve) {
            student = new Student(request);
            users.add(student);
        }

        try {
            save();
        } catch (IOException e) {
            // Undo the decision in memory if saving fails.
            request.setDecision("Pending", 0);

            if (student != null) {
                users.remove(student);
            }

            // Pass the file error to the calling code.
            throw e;
        }
    }

    // Writes a file header followed by seven lines for each request.
    private void save() throws IOException {
        PrintWriter output = new PrintWriter(new FileWriter(filename));

        try {
            // Identifies the expected account file format.
            output.println("GPU_ACCOUNTS_V1");

            for (int i = 0; i < requests.size(); i++) {
                AccessRequest request = requests.get(i);

                output.println(request.getId());
                output.println(request.getName());
                output.println(request.getEmail());
                output.println(request.getPhone());
                output.println(request.getPassword());
                output.println(request.getStatus());
                output.println(request.getMinutes());
            }

            // PrintWriter reports writing failures through checkError().
            if (output.checkError()) {
                throw new IOException("Could not save accounts.");
            }
        } finally {
            // Close the writer even if an error occurs.
            output.close();
        }

        // Check whether closing the writer caused an error.
        if (output.checkError()) {
            throw new IOException("Could not close the saved account file.");
        }
    }

    // Reads one required line and reports missing data.
    private String readLine(Scanner input) throws IOException {
        if (!input.hasNextLine()) {
            throw new IOException("Incomplete account file.");
        }

        return input.nextLine();
    }

    // Loads saved requests and recreates approved student accounts.
    private void load() throws IOException, AccountException {
        File file = new File(filename);

        // A missing file means there is no saved data to load yet.
        if (!file.exists()) {
            return;
        }

        Scanner input = new Scanner(file);

        try {
            // Reject files that do not have the expected header.
            if (!readLine(input).equals("GPU_ACCOUNTS_V1")) {
                throw new IOException("Invalid account file.");
            }

            while (input.hasNextLine()) {
                // Read fields in the same order used by save().
                String id = readLine(input);
                String name = readLine(input);
                String email = readLine(input);
                String phone = readLine(input);
                String password = readLine(input);
                String status = readLine(input);

                int minutes;

                try {
                    // Convert the saved text into an integer.
                    minutes = Integer.parseInt(readLine(input));
                } catch (NumberFormatException e) {
                    // Report a saved allowance that is not a valid integer.
                    throw new IOException("Invalid saved minutes.");
                }

                validate(id, name, email, phone, password);

                // Prevent duplicate accounts from being loaded.
                if (findRequest(id) != null) {
                    throw new IOException("Duplicate ID in account file.");
                }

                // Only these three registration statuses are valid.
                if (!status.equals("Pending") && !status.equals("Approved") && !status.equals("Rejected")) {
                    throw new IOException("Invalid saved request status.");
                }

                // Minutes must be nonnegative; unapproved requests must have zero.
                if (minutes < 0 || (!status.equals("Approved") && minutes != 0)) {
                    throw new IOException("Invalid saved allowance.");
                }

                AccessRequest request = new AccessRequest(id, name, email, phone, password);
                request.setDecision(status, minutes);
                requests.add(request);

                // Restore login access for approved students.
                if (status.equals("Approved")) {
                    users.add(new Student(request));
                }
            }
        } finally {
            // Close the scanner even if loading fails.
            input.close();
        }
    }
}