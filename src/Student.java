public class Student extends User {

    private String email;
    private String phone;
    private int initialMinutes;

    private TimeAllowance timeAllowance;

    public Student(AccessRequest request) {

        super(request.getId(), request.getName(), request.getPassword());

        email = request.getEmail();
        phone = request.getPhone();
        initialMinutes = request.getMinutes();

        timeAllowance = new TimeAllowance(request.getId(), initialMinutes, 0, 0);
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public int getInitialMinutes() {
        return initialMinutes;
    }

    public TimeAllowance getTimeAllowance() {
        return timeAllowance;
    }

    public void setTimeAllowance(TimeAllowance timeAllowance) {

        if (timeAllowance != null && timeAllowance.getStudentId().equals(getUsername())) {
            this.timeAllowance = timeAllowance;
        }
    }

    @Override
    public String getRole() {
        return "Student";
    }
}