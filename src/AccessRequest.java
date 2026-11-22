public class AccessRequest {
    private String id;
    private String name;
    private String email;
    private String phone;
    private String password;
    private String status = "Pending";
    private int minutes = 0;

    public AccessRequest(String id,String name,String email,String phone,String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }

    public String getStatus() {
        return status;
    }

    public int getMinutes() {
        return minutes;
    }

    // AccountManager calls this after checking permissions.
    void setDecision(String status, int minutes) {
        this.status = status;
        this.minutes = minutes;
    }
}