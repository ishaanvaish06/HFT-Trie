import java.util.*;

public class AnomalyDetector {

    public static class AnomalyIncident implements Comparable<AnomalyIncident> {
        private final String windowStart;
        private final String windowEnd;
        private final int errorCount;
        private final double baselineMean;
        private final double standardDeviation;
        private final double zScore;
        private final String primaryService;
        private final Map<String, Integer> serviceBreakdown;

        public AnomalyIncident(
                String windowStart,
                String windowEnd,
                int errorCount,
                double baselineMean,
                double standardDeviation,
                double zScore,
                String primaryService,
                Map<String, Integer> serviceBreakdown) {
            this.windowStart = windowStart;
            this.windowEnd = windowEnd;
            this.errorCount = errorCount;
            this.baselineMean = baselineMean;
            this.standardDeviation = standardDeviation;
            this.zScore = zScore;
            this.primaryService = primaryService;
            this.serviceBreakdown = serviceBreakdown;
        }

        public String getWindowStart() { return windowStart; }
        public String getWindowEnd() { return windowEnd; }
        public int getErrorCount() { return errorCount; }
        public double getBaselineMean() { return baselineMean; }
        public double getStandardDeviation() { return standardDeviation; }
        public double getZScore() { return zScore; }
        public String getPrimaryService() { return primaryService; }
        public Map<String, Integer> getServiceBreakdown() { return serviceBreakdown; }

        @Override
        public int compareTo(AnomalyIncident o) {

            return Double.compare(o.zScore, this.zScore);
        }

        public String toFormattedString() {
            return String.format(
                    "[%s -> %s] Errors: %,d (Baseline: %.1f, Z-Score: +%.2f) | Primary Culprit: %s",
                    windowStart, windowEnd, errorCount, baselineMean, zScore, primaryService
            );
        }
    }

    private static class WindowBucket {
        long startEpoch;
        long endEpoch;
        int errorCount = 0;
        Map<String, Integer> serviceCounts = new HashMap<>();

        WindowBucket(long startEpoch, long endEpoch) {
            this.startEpoch = startEpoch;
            this.endEpoch = endEpoch;
        }
    }

    public static List<AnomalyIncident> detectAnomalies(List<LogRecord> dataset) {
        return detectAnomalies(dataset, 5 * 60 * 1000L, 2.5);
    }

    public static List<AnomalyIncident> detectAnomalies(
            List<LogRecord> dataset,
            long windowSizeMillis,
            double zThreshold) {

        List<AnomalyIncident> incidents = new ArrayList<>();
        if (dataset == null || dataset.isEmpty()) return incidents;

        long minEpoch = Long.MAX_VALUE;
        long maxEpoch = Long.MIN_VALUE;
        for (LogRecord r : dataset) {
            long e = r.getEpochMillis();
            if (e < minEpoch) minEpoch = e;
            if (e > maxEpoch) maxEpoch = e;
        }

        if (minEpoch == Long.MAX_VALUE || minEpoch >= maxEpoch) return incidents;

        int numBuckets = (int) Math.ceil((double) (maxEpoch - minEpoch + 1) / windowSizeMillis);
        if (numBuckets < 3) {

            return incidents;
        }

        WindowBucket[] buckets = new WindowBucket[numBuckets];
        for (int i = 0; i < numBuckets; i++) {
            long start = minEpoch + (i * windowSizeMillis);
            long end = start + windowSizeMillis - 1;
            buckets[i] = new WindowBucket(start, end);
        }

        for (LogRecord r : dataset) {
            if ("ERROR".equalsIgnoreCase(r.getLevel()) || "WARN".equalsIgnoreCase(r.getLevel())) {
                int bIdx = (int) ((r.getEpochMillis() - minEpoch) / windowSizeMillis);
                if (bIdx >= 0 && bIdx < numBuckets) {
                    buckets[bIdx].errorCount++;
                    buckets[bIdx].serviceCounts.put(
                            r.getService(),
                            buckets[bIdx].serviceCounts.getOrDefault(r.getService(), 0) + 1
                    );
                }
            }
        }

        double sum = 0.0;
        for (WindowBucket b : buckets) {
            sum += b.errorCount;
        }
        double mean = sum / numBuckets;

        double varianceSum = 0.0;
        for (WindowBucket b : buckets) {
            double diff = b.errorCount - mean;
            varianceSum += diff * diff;
        }
        double stdDev = Math.sqrt(varianceSum / (numBuckets - 1));

        if (stdDev < 0.0001) {

            return incidents;
        }

        for (WindowBucket b : buckets) {
            double z = (b.errorCount - mean) / stdDev;
            if (z >= zThreshold) {
                String primaryService = "None";
                int maxServiceErrors = -1;
                for (Map.Entry<String, Integer> entry : b.serviceCounts.entrySet()) {
                    if (entry.getValue() > maxServiceErrors) {
                        maxServiceErrors = entry.getValue();
                        primaryService = entry.getKey();
                    }
                }

                incidents.add(new AnomalyIncident(
                        LogRecord.formatEpoch(b.startEpoch),
                        LogRecord.formatEpoch(b.endEpoch),
                        b.errorCount,
                        mean,
                        stdDev,
                        z,
                        primaryService,
                        new HashMap<>(b.serviceCounts)
                ));
            }
        }

        Collections.sort(incidents);
        return incidents;
    }
}
