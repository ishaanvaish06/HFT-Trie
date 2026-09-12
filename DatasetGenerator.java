import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class DatasetGenerator {

    private static final String[] SERVICES = {
        "API", "Database", "Authentication", "Payment", "Server", "Network", "Storage"
    };

    private static final String[] LEVELS = {
        "INFO", "WARN", "ERROR", "DEBUG"
    };

    private static final String[] MESSAGES = {
        "Connection timeout",
        "Connection refused",
        "Authentication failed",
        "Database unavailable",
        "Request completed",
        "High memory usage",
        "File not found",
        "Invalid request",
        "Server overloaded",
        "Payment failed"
    };

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void generateDataset(String filePath, int count) throws IOException {
        File file = new File(filePath);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        Random rand = new Random(42);
        LocalDateTime baseTime = LocalDateTime.of(2026, 8, 28, 0, 0, 0);

        long start = System.currentTimeMillis();
        System.out.printf("Generating %s logs into %s... ", String.format("%,d", count), filePath);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file), 1024 * 1024)) {

            bw.write("LogID,Timestamp,Level,Service,Message\n");

            LocalDateTime currentTime = baseTime;
            for (int i = 1; i <= count; i++) {
                long logId = 10000000L + i;

                currentTime = currentTime.plusSeconds(rand.nextInt(4));
                String tsStr = currentTime.format(FORMATTER);

                String level = LEVELS[rand.nextInt(LEVELS.length)];
                String service = SERVICES[rand.nextInt(SERVICES.length)];
                String message = MESSAGES[rand.nextInt(MESSAGES.length)];

                LogRecord record = new LogRecord(logId, tsStr, level, service, message);
                bw.write(record.toCsvLine());
                bw.write("\n");
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("Done in %d ms.\n", elapsed);
    }

    public static void main(String[] args) {
        String dataDir = "data";
        int[] sizes = {10000, 50000, 100000, 500000, 1000000};
        String[] fileNames = {
            "logs_10k.csv",
            "logs_50k.csv",
            "logs_100k.csv",
            "logs_500k.csv",
            "logs_1m.csv"
        };

        System.out.println("=========================================");
        System.out.println("       LOG DATASET GENERATOR            ");
        System.out.println("=========================================");

        try {
            for (int i = 0; i < sizes.length; i++) {
                String path = dataDir + File.separator + fileNames[i];
                generateDataset(path, sizes[i]);
            }
            System.out.println("\nAll datasets generated successfully in the /data directory.");
        } catch (IOException e) {
            System.err.println("Error generating datasets: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
