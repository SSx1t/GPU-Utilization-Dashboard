public abstract class User {
    private String username;
    private String name;
    private String password;

    public User(String username, String name, String password) {
        this.username = username;
        this.name = name;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public boolean checkPassword(String value) {
        return password.equals(value);
    }

    // Each subclass provides its own role.
    public abstract String getRole();
}
