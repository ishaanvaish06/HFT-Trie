import java.util.ArrayList;
import java.util.List;

public class LinearTextSearch {

    public static List<LogRecord> searchKeyword(List<LogRecord> dataset, String keyword) {
        List<LogRecord> results = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) return results;
        String queryLower = keyword.toLowerCase();

        for (LogRecord record : dataset) {
            if (record.getMessage().toLowerCase().contains(queryLower) ||
                record.getService().toLowerCase().contains(queryLower) ||
                record.getLevel().toLowerCase().contains(queryLower)) {
                results.add(record);
            }
        }
        return results;
    }

    public static List<LogRecord> searchPrefix(List<LogRecord> dataset, String prefix) {
        List<LogRecord> results = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) return results;
        String prefixLower = prefix.toLowerCase();

        for (LogRecord record : dataset) {
            String[] words = (record.getMessage() + " " + record.getService()).toLowerCase().split("\\W+");
            for (String word : words) {
                if (word.startsWith(prefixLower)) {
                    results.add(record);
                    break;
                }
            }
        }
        return results;
    }

    public static List<LogRecord> searchWildcard(List<LogRecord> dataset, String pattern) {
        List<LogRecord> results = new ArrayList<>();
        if (pattern == null || pattern.isEmpty()) return results;
        String patLower = pattern.toLowerCase();

        for (LogRecord record : dataset) {
            String msg = record.getMessage().toLowerCase();
            String svc = record.getService().toLowerCase();
            String lvl = record.getLevel().toLowerCase();
            if (matchesGlob(patLower, msg) || matchesGlob(patLower, svc) || matchesGlob(patLower, lvl)) {
                results.add(record);
            }
        }
        return results;
    }

    public static boolean matchesGlob(String pattern, String text) {
        int patLen = pattern.length();
        int txtLen = text.length();
        int pi = 0, ti = 0;
        int starPi = -1, starTi = -1;

        while (ti < txtLen) {
            if (pi < patLen && (pattern.charAt(pi) == '?' || pattern.charAt(pi) == text.charAt(ti))) {
                pi++;
                ti++;
            } else if (pi < patLen && pattern.charAt(pi) == '*') {
                starPi = pi;
                starTi = ti;
                pi++;
            } else if (starPi != -1) {
                pi = starPi + 1;
                starTi++;
                ti = starTi;
            } else {
                return false;
            }
        }

        while (pi < patLen && pattern.charAt(pi) == '*') pi++;
        return pi == patLen;
    }
}
