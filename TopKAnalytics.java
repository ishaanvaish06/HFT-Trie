import java.util.*;

public class TopKAnalytics {

    public static class FrequencyEntry implements Comparable<FrequencyEntry> {
        public String key;
        public int count;

        public FrequencyEntry(String key, int count) {
            this.key = key;
            this.count = count;
        }

        @Override
        public int compareTo(FrequencyEntry o) {
            return Integer.compare(this.count, o.count);
        }
    }

    public static List<FrequencyEntry> getTopKErrors(List<LogRecord> dataset, int k) {
        Map<String, Integer> freqMap = new HashMap<>();

        for (LogRecord record : dataset) {
            if ("ERROR".equalsIgnoreCase(record.getLevel()) || "WARN".equalsIgnoreCase(record.getLevel())) {
                String errorType = record.getService() + " - " + record.getMessage();
                freqMap.put(errorType, freqMap.getOrDefault(errorType, 0) + 1);
            }
        }

        PriorityQueue<FrequencyEntry> minHeap = new PriorityQueue<>(k);

        for (Map.Entry<String, Integer> entry : freqMap.entrySet()) {
            FrequencyEntry fe = new FrequencyEntry(entry.getKey(), entry.getValue());
            if (minHeap.size() < k) {
                minHeap.add(fe);
            } else if (entry.getValue() > minHeap.peek().count) {
                minHeap.poll();
                minHeap.add(fe);
            }
        }

        List<FrequencyEntry> result = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            result.add(minHeap.poll());
        }
        Collections.reverse(result);
        return result;
    }
}
