import java.util.ArrayList;

public class TimeAllowance {

    private String studentId;

    private int allocatedMinutes;
    private int usedMinutes;
    private int reservedMinutes;

    // Stores the history of additional time granted
    private ArrayList<String> grantHistory;

    // Constructor for a newly approved student
    public TimeAllowance(String studentId) {

        this.studentId = studentId;

        allocatedMinutes = 100;
        usedMinutes = 0;
        reservedMinutes = 0;

        grantHistory = new ArrayList<String>();
    }

    // Constructor for information loaded from a file
    public TimeAllowance(String studentId, int allocatedMinutes, int usedMinutes, int reservedMinutes) {

        this.studentId = studentId;
        this.allocatedMinutes = allocatedMinutes;
        this.usedMinutes = usedMinutes;
        this.reservedMinutes = reservedMinutes;

        grantHistory = new ArrayList<String>();
    }

    public String getStudentId() {
        return studentId;
    }

    public int getAllocatedMinutes() {
        return allocatedMinutes;
    }

    public int getUsedMinutes() {
        return usedMinutes;
    }

    public int getReservedMinutes() {
        return reservedMinutes;
    }

    public int getAvailableMinutes() {
        return allocatedMinutes - usedMinutes - reservedMinutes;
    }

    public ArrayList<String> getGrantHistory() {
        return grantHistory;
    }

    public boolean reserveMinutes(int duration) {

        if (duration > 0 && duration <= getAvailableMinutes()) {
            reservedMinutes += duration;
            return true;
        }

        return false;
    }

    public boolean releaseReservation(int duration) {

        if (duration > 0 && duration <= reservedMinutes) {
            reservedMinutes -= duration;
            return true;
        }

        return false;
    }

    public boolean settleTime(int reservedDuration, int chargedMinutes) {

        if (reservedDuration <= 0 || chargedMinutes < 0) {
            return false;
        }

        if (reservedDuration > reservedMinutes) {
            return false;
        }

        if (chargedMinutes > reservedDuration) {
            return false;
        }

        reservedMinutes -= reservedDuration;
        usedMinutes += chargedMinutes;

        return true;
    }

    public boolean grantExtraTime(int minutes, String reason, String time) {

        if (minutes <= 0 || reason == null || reason.trim().equals("")) {
            return false;
        }

        allocatedMinutes += minutes;

        String record = time
                + " | Student ID: " + studentId
                + " | Added " + minutes
                + " minutes"
                + " | Reason: " + reason;

        grantHistory.add(record);

        return true;
    }

    public boolean adjustAllocatedMinutes(int newAmount) {

        if (newAmount >= usedMinutes + reservedMinutes) {
            allocatedMinutes = newAmount;
            return true;
        }

        return false;
    }

    public String toString() {

        return "Student ID: " + studentId
                + ", Allocated: " + allocatedMinutes
                + ", Used: " + usedMinutes
                + ", Reserved: " + reservedMinutes
                + ", Available: " + getAvailableMinutes();
    }
}