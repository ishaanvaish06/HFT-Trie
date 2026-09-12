import java.util.ArrayList;
import java.util.List;

public class LinearTimeSearch {

    public static List<LogRecord> searchExact(List<LogRecord> dataset, long epochMillis) {
        List<LogRecord> results = new ArrayList<>();
        for (LogRecord record : dataset) {
            if (record.getEpochMillis() == epochMillis) {
                results.add(record);
            }
        }
        return results;
    }

    public static List<LogRecord> searchRange(List<LogRecord> dataset, long startEpoch, long endEpoch) {
        List<LogRecord> results = new ArrayList<>();
        for (LogRecord record : dataset) {
            if (record.getEpochMillis() >= startEpoch && record.getEpochMillis() <= endEpoch) {
                results.add(record);
            }
        }
        return results;
    }
}
