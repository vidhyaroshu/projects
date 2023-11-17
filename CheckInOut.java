import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import redis.clients.jedis.Jedis;
import java.util.*;
import java.io.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.hssf.usermodel.HSSFWorkbook; 

public class CheckInOut {

    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        Scanner input = new Scanner(System.in);

            System.out.println("Enter the number of users: ");
            int numberOfUsers = input.nextInt();

                 for (int i = 0; i < numberOfUsers; i++) {

                    System.out.println("Enter employee ID for user " + (i + 1) + ": ");
                    String employeeId = input.next();

                    if (employeeId == null) {
                        System.out.println("Invalid employee ID. Please enter a valid ID.");
                        continue;
                    }


                    Thread employeeThread=new Thread(new EmployeeThread(employeeId));
                    employeeThread.start();
    
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


public static boolean doesSheetExist(Workbook workbook, String sheetName) {
        
        return workbook.getSheet(sheetName) != null;
    }
    protected void employeeCheckInOut(String employeeId, Jedis jedis){

        String workbookPath = "EmployeeCheckInOut.xls";

        File file = new File(workbookPath);
        Workbook workbook=null;
        boolean sheetExists=false;
        Sheet sheet=null;

            if (file.exists()) {
                 String sheetName= "Employee_" + employeeId;
      
                try (FileInputStream fileInputStream = new FileInputStream(workbookPath)) {
                workbook= new HSSFWorkbook(fileInputStream);
                  sheetExists = doesSheetExist(workbook, sheetName);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } 
            else {
                workbook = new HSSFWorkbook();
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    workbook.write(fileOut);
                }
                catch(Exception e){
                    e.printStackTrace();
                }
            }

       try (FileOutputStream fileOut = new FileOutputStream("EmployeeCheckInOut.xls")) {

            if (!sheetExists) {

                sheet = workbook.createSheet("Employee_" + employeeId);
                Row row = sheet.createRow(0);
                row.createCell(0).setCellValue("Day");
                row.createCell(1).setCellValue("checkin_time");
                row.createCell(2).setCellValue("checkout_time");
                row.createCell(3).setCellValue("checkin_status");
                row.createCell(4).setCellValue("workingHours");

            } 
            else{
                sheet = workbook.getSheet("Employee_" + employeeId);
            }

            if(jedis.hget(getCurrentDate(),employeeId)==null){
                    int lastRowNum = sheet.getLastRowNum();
                    Map<String, String> innerHash = new HashMap<>();
                    innerHash.put("checkin_time",getCurrentTime());
                    innerHash.put("checkout_time",getCurrentTime());
                    innerHash.put("checkin_status","checked_in");
                    innerHash.put("workingHours","0");

                    String innerHashAsString = convertMapToString(innerHash);
                    jedis.hset(getCurrentDate(),employeeId,innerHashAsString);

                        Row row = sheet.createRow(lastRowNum+1);
                        row.createCell(0).setCellValue(getCurrentDate());
                        row.createCell(1).setCellValue(getCurrentTime());
                        row.createCell(2).setCellValue(getCurrentTime());
                        row.createCell(3).setCellValue("checked_in");
                        row.createCell(4).setCellValue("0 seconds");

                    System.out.println("successfully checked In");
                }
                else{
                    String storedInnerHashAsString = jedis.hget(getCurrentDate(), employeeId);
                    Map<String, String> storedInnerHash = convertStringToMap(storedInnerHashAsString);
                    String status=storedInnerHash.get("checkin_status");
                    if(status.equals("checked_in")){
                        storedInnerHash.put("checkin_status","checked_out");
                        storedInnerHash.put("checkout_time",getCurrentTime());
                        long totalSeconds=calculateWorkingHours(storedInnerHash.get("checkin_time"),storedInnerHash.get("checkout_time"));

                        storedInnerHash.put("workingHours",String.valueOf(totalSeconds));
                        String innerHashAsString = convertMapToString(storedInnerHash);
                        jedis.hset(getCurrentDate(),employeeId,innerHashAsString);
                        int lastRowNum = sheet.getLastRowNum();
                        Row lastRow = sheet.getRow(lastRowNum);
                        lastRow.getCell(3).setCellValue("checked_out");
                        lastRow.getCell(2).setCellValue(getCurrentTime());
                        lastRow.getCell(4).setCellValue(totalSeconds / 3600 + " Hours " + (totalSeconds % 3600) / 60 + " Minutes " + totalSeconds % 60+" Seconds");
                        System.out.println("successfully checked Out");

                        System.out.println(totalSeconds / 3600 + " Hours " + (totalSeconds % 3600) / 60 + " Minutes " + totalSeconds % 60+" Seconds");
                    }
                    else{

                        storedInnerHash.put("checkin_status","checked_in");
                        String innerHashAsString = convertMapToString(storedInnerHash);
                        jedis.hset(getCurrentDate(),employeeId,innerHashAsString);

                        int lastRowNum = sheet.getLastRowNum();
                        Row lastRow = sheet.getRow(lastRowNum);
                        lastRow.getCell(3).setCellValue("checked_out");
                        System.out.println("successfully checked In");
                    }
                }

                 workbook.write(fileOut);
        }catch(Exception e){
            e.printStackTrace();
        }
    } 
}

class EmployeeThread implements Runnable {
    private String employeeId;
        EmployeeThread(String employeeId) {
            this.employeeId = employeeId;
        }
    public void run() {
        try(Jedis jedis = new Jedis("localhost", 6379)){

            new CheckInOut().employeeCheckInOut(employeeId,jedis);

        }catch(Exception e){
            e.printStackTrace();
        }

    }
}
