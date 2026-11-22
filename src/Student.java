public class Student extends User {
    private String email;
    private String phone;
    private int initialMinutes;

    public Student(AccessRequest request) {
        super(request.getId(),request.getName(),request.getPassword());
        email = request.getEmail();
        phone = request.getPhone();
        initialMinutes = request.getMinutes();
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

    @Override
    public String getRole() {
        return "Student";
    }
}