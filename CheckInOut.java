import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import redis.clients.jedis.Jedis;
import java.util.*;

public class CheckInOut {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        try(Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)){


            Scanner input = new Scanner(System.in);
            System.out.println("Enter the number of users: ");
            int numberOfUsers = input.nextInt();

            for (int i = 0; i < numberOfUsers; i++) {
                System.out.println("Enter employee ID for user " + (i + 1) + ": ");
                String employeeId = input.next();

                if (employeeId == null || employeeId.trim().isEmpty()) {
                    System.out.println("Invalid employee ID. Please enter a valid ID.");
                    continue;
                }
                Thread employeeThread=new Thread(new EmployeeThread(employeeId,jedis));
                employeeThread.start();
            }
        }
        catch(Exception e){
            e.printStackTrace();
        }

                
    }
     private static String convertMapToString(Map<String, String> map) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            stringBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append(",");
        }
        return stringBuilder.toString();
    }
     private static Map<String, String> convertStringToMap(String str) {
        Map<String, String> map = new HashMap<>();
        if (str != null && !str.isEmpty()) {
            String[] pairs = str.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                if (keyValue.length == 2) {
                    map.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return map;
    }
    private static String getCurrentDate(){
         LocalDate currentDate = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd"); //No I18N
        String formattedDate = currentDate.format(formatter);
        return formattedDate;
    }

    private static String getCurrentTime() {
        return LocalDateTime.now().format(formatter);
    }

    private static long calculateWorkingHours(String checkinTime, String checkoutTime) {
        long checkinTimestamp = Timestamp.valueOf(checkinTime).getTime();
        long checkoutTimestamp = Timestamp.valueOf(checkoutTime).getTime();
        return (checkoutTimestamp - checkinTimestamp) / 1000;
    }
    protected void employeeCheckInOut(String employeeId, Jedis jedis){
        try{
            if(jedis.hget(getCurrentDate(),employeeId)==null){
                    Map<String, String> innerHash = new HashMap<>();
                    innerHash.put("checkin_time",getCurrentTime());
                    innerHash.put("checkout_time",getCurrentTime());
                    innerHash.put("checkin_status","checked_in");
                    innerHash.put("workingHours","0");

                    String innerHashAsString = convertMapToString(innerHash);
                    jedis.hset(getCurrentDate(),employeeId,innerHashAsString);
                    System.out.println("successfully checked In");
                }
                else{
                    String storedInnerHashAsString = jedis.hget(getCurrentDate(), employeeId);
                    Map<String, String> storedInnerHash = convertStringToMap(storedInnerHashAsString);
                    String status=storedInnerHash.get("checkin_status");
                    if(status.equals("checked_in")){
                        String workHours=storedInnerHash.get("workingHours");
                        
                        long totalSeconds=Long.parseLong(workHours);  
                        storedInnerHash.put("checkin_status","checked_out");
                        storedInnerHash.put("checkout_time",getCurrentTime());
                        totalSeconds+=calculateWorkingHours(storedInnerHash.get("checkin_time"),storedInnerHash.get("checkout_time"));

                        storedInnerHash.put("workingHours",String.valueOf(totalSeconds));
                        String innerHashAsString = convertMapToString(storedInnerHash);
                        jedis.hset(getCurrentDate(),employeeId,innerHashAsString);
                        System.out.println("successfully checked Out");

                        System.out.println(totalSeconds / 3600 + " Hours " + (totalSeconds % 3600) / 60 + " Minutes " + totalSeconds % 60+" Seconds");
                    }
                    else{

                        storedInnerHash.put("checkin_status","checked_in");
                        storedInnerHash.put("checkin_time",getCurrentTime());
                        String innerHashAsString = convertMapToString(storedInnerHash);
                        jedis.hset(getCurrentDate(),employeeId,innerHashAsString);
                        System.out.println("successfully checked In");
                    }
                }
        }catch(Exception e){
            e.printStackTrace();
        }
    } 



}
class EmployeeThread implements Runnable {
    private String employeeId;
        private Jedis jedis;

        EmployeeThread(String employeeId, Jedis jedis) {
            this.employeeId = employeeId;
            this.jedis = jedis;
        }
    public void run() {

        new CheckInOut().employeeCheckInOut(employeeId,jedis);
    }
}
