import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class ClusterCoordinator {

    private final int numShards;
    private final ShardWorker[] shards;
    private final ExecutorService threadPool;

    public ClusterCoordinator() {
        this(3);
    }

    public ClusterCoordinator(int numShards) {
        if (numShards <= 0) {
            throw new IllegalArgumentException("Number of shards must be positive.");
        }
        this.numShards = numShards;
        this.shards = new ShardWorker[numShards];
        for (int i = 0; i < numShards; i++) {
            this.shards[i] = new ShardWorker(i);
        }

        this.threadPool = Executors.newFixedThreadPool(numShards, new ThreadFactory() {
            private int counter = 0;
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "shard-worker-" + (counter++));
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void ingest(LogRecord record) {
        int shardIndex = Math.floorMod(Long.hashCode(record.getLogId()), numShards);
        shards[shardIndex].insertRecord(record);
    }

    public void ingestBatch(List<LogRecord> records) {
        for (LogRecord record : records) {
            ingest(record);
        }
    }

    public boolean delete(LogRecord record) {
        if (record == null) return false;
        int shardIndex = Math.floorMod(Long.hashCode(record.getLogId()), numShards);
        return shards[shardIndex].deleteRecord(record);
    }

    public boolean deleteById(long logId) {
        int shardIndex = Math.floorMod(Long.hashCode(logId), numShards);
        ShardWorker worker = shards[shardIndex];
        LogRecord target = null;
        for (LogRecord r : worker.getRecords()) {
            if (r.getLogId() == logId) {
                target = r;
                break;
            }
        }
        if (target != null) {
            return worker.deleteRecord(target);
        }
        return false;
    }

    public List<LogRecord> executeScatterGatherQuery(
            String keyword,
            String prefix,
            String wildcardPattern,
            String logLevel,
            String service,
            Long startEpoch,
            Long endEpoch) {

        List<CompletableFuture<List<LogRecord>>> futures = new ArrayList<>(numShards);
        for (int i = 0; i < numShards; i++) {
            final ShardWorker worker = shards[i];
            futures.add(CompletableFuture.supplyAsync(() -> worker.executeLocalQuery(
                    keyword,
                    prefix,
                    wildcardPattern,
                    logLevel,
                    service,
                    startEpoch,
                    endEpoch
            ), threadPool));
        }

        List<List<LogRecord>> shardResults = new ArrayList<>(numShards);
        for (CompletableFuture<List<LogRecord>> future : futures) {
            shardResults.add(future.join());
        }

        return kWayMerge(shardResults);
    }

    private static class ShardCursor implements Comparable<ShardCursor> {
        final List<LogRecord> list;
        int index;

        ShardCursor(List<LogRecord> list) {
            this.list = list;
            this.index = 0;
        }

        boolean hasNext() {
            return index < list.size();
        }

        LogRecord peek() {
            return list.get(index);
        }

        LogRecord poll() {
            return list.get(index++);
        }

        @Override
        public int compareTo(ShardCursor other) {
            return Long.compare(this.peek().getEpochMillis(), other.peek().getEpochMillis());
        }
    }

    public static List<LogRecord> kWayMerge(List<List<LogRecord>> shardResults) {
        if (shardResults == null || shardResults.isEmpty()) {
            return new ArrayList<>();
        }

        PriorityQueue<ShardCursor> minHeap = new PriorityQueue<>(shardResults.size());
        int totalCapacity = 0;

        for (List<LogRecord> list : shardResults) {
            if (list != null && !list.isEmpty()) {
                minHeap.add(new ShardCursor(list));
                totalCapacity += list.size();
            }
        }

        List<LogRecord> merged = new ArrayList<>(totalCapacity);
        while (!minHeap.isEmpty()) {
            ShardCursor cursor = minHeap.poll();
            merged.add(cursor.poll());
            if (cursor.hasNext()) {
                minHeap.add(cursor);
            }
        }

        return merged;
    }

    public int getNumShards() {
        return numShards;
    }

    public ShardWorker getShard(int index) {
        return shards[index];
    }

    public int getTotalRecords() {
        int total = 0;
        for (ShardWorker worker : shards) {
            total += worker.getRecordCount();
        }
        return total;
    }

    public int[] getShardDistribution() {
        int[] counts = new int[numShards];
        for (int i = 0; i < numShards; i++) {
            counts[i] = shards[i].getRecordCount();
        }
        return counts;
    }

    public void clear() {
        for (ShardWorker worker : shards) {
            worker.clear();
        }
    }

    public void shutdown() {
        threadPool.shutdown();
    }
}
