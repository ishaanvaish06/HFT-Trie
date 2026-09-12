import java.util.*;

public class LogTemplateMiner {

    public static class TemplateCluster implements Comparable<TemplateCluster> {
        private final String template;
        private int count;
        private final Set<String> services;
        private final Set<String> levels;
        private final List<String> sampleOriginals;

        public TemplateCluster(String template) {
            this.template = template;
            this.count = 0;
            this.services = new HashSet<>();
            this.levels = new HashSet<>();
            this.sampleOriginals = new ArrayList<>(3);
        }

        public void addRecord(LogRecord record) {
            count++;
            services.add(record.getService());
            levels.add(record.getLevel());
            if (sampleOriginals.size() < 3) {
                sampleOriginals.add(record.getMessage());
            }
        }

        public String getTemplate() { return template; }
        public int getCount() { return count; }
        public Set<String> getServices() { return services; }
        public Set<String> getLevels() { return levels; }
        public List<String> getSampleOriginals() { return sampleOriginals; }

        @Override
        public int compareTo(TemplateCluster o) {

            return Integer.compare(o.count, this.count);
        }
    }

    public static List<TemplateCluster> mineTemplates(List<LogRecord> records) {
        Map<String, TemplateCluster> clusterMap = new HashMap<>();

        for (LogRecord record : records) {
            String template = extractTemplate(record.getMessage());
            TemplateCluster cluster = clusterMap.computeIfAbsent(template, TemplateCluster::new);
            cluster.addRecord(record);
        }

        List<TemplateCluster> result = new ArrayList<>(clusterMap.values());
        Collections.sort(result);
        return result;
    }

    public static String extractTemplate(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "<EMPTY>";
        }

        String[] tokens = message.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String normalized = normalizeToken(token);
            sb.append(normalized);
            if (i < tokens.length - 1) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private static String normalizeToken(String token) {

        String clean = token.replaceAll("[.,;:!?()\"\']", "");
        if (clean.isEmpty()) return token;

        if (clean.matches("^\\d+$")) {
            return "<*>";
        }

        if (clean.matches("(?i)^0x[0-9a-f]+$")) {
            return "<*>";
        }

        if (clean.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            return "<*>";
        }

        if (clean.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?$")) {
            return "<*>";
        }

        if (clean.matches(".*\\d.*") && clean.length() > 2) {
            return "<*>";
        }

        return token;
    }
}
