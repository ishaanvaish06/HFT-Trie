import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class LogRecord {
    private long logId;
    private String timestamp;
    private long epochMillis;
    private String level;
    private String service;
    private String message;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public LogRecord(long logId, String timestamp, String level, String service, String message) {
        this.logId = logId;
        this.timestamp = timestamp;
        this.epochMillis = parseEpoch(timestamp);
        this.level = level;
        this.service = service;
        this.message = message;
    }

    public LogRecord(long logId, long epochMillis, String level, String service, String message) {
        this.logId = logId;
        this.epochMillis = epochMillis;
        this.timestamp = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(FORMATTER);
        this.level = level;
        this.service = service;
        this.message = message;
    }

    public static long parseEpoch(String tsStr) {
        try {
            LocalDateTime ldt = LocalDateTime.parse(tsStr, FORMATTER);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    public static String formatEpoch(long epochMillis) {
        LocalDateTime ldt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
        return ldt.format(FORMATTER);
    }

    public long getLogId() { return logId; }
    public String getTimestamp() { return timestamp; }
    public long getEpochMillis() { return epochMillis; }
    public String getLevel() { return level; }
    public String getService() { return service; }
    public String getMessage() { return message; }

    public String toCsvLine() {
        return logId + "," + timestamp + "," + level + "," + service + ",\"" + message.replace("\"", "\"\"") + "\"";
    }

    public static LogRecord fromCsvLine(String line) {
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        if (parts.length < 5) return null;
        long id = Long.parseLong(parts[0].trim());
        String ts = parts[1].trim();
        String lvl = parts[2].trim();
        String svc = parts[3].trim();
        String msg = parts[4].trim().replaceAll("^\"|\"$", "").replace("\"\"", "\"");
        return new LogRecord(id, ts, lvl, svc, msg);
    }

    public String toFormattedString() {
        return String.format("%-6d | %s | %-5s | %-14s | %s", logId, timestamp, level, service, message);
    }

    @Override
    public String toString() {
        return toFormattedString();
    }
}
