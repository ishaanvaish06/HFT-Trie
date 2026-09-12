import java.util.ArrayList;
import java.util.List;

public class ShardWorker {

    private final int shardId;
    private final List<LogRecord> records;
    private final CompressedTrie trie;
    private final FusionTree fusionTree;
    private final QueryEngine queryEngine;

    public ShardWorker(int shardId) {
        this.shardId = shardId;
        this.records = new ArrayList<>();
        this.trie = new CompressedTrie();
        this.fusionTree = new FusionTree();
        this.queryEngine = new QueryEngine(trie, fusionTree);
    }

    public synchronized void insertRecord(LogRecord record) {
        records.add(record);
        trie.insertRecord(record);
        fusionTree.insert(record);
    }

    public synchronized void insertBatch(List<LogRecord> batch) {
        for (LogRecord record : batch) {
            insertRecord(record);
        }
    }

    public List<LogRecord> executeLocalQuery(
            String keyword,
            String prefix,
            String wildcardPattern,
            String logLevel,
            String service,
            Long startEpoch,
            Long endEpoch) {
        return queryEngine.executeCombinedQuery(
                keyword,
                prefix,
                wildcardPattern,
                logLevel,
                service,
                startEpoch,
                endEpoch,
                records
        );
    }

    public synchronized boolean deleteRecord(LogRecord record) {
        if (record == null) return false;
        boolean removed = records.removeIf(r -> r.getLogId() == record.getLogId());
        if (removed) {
            trie.deleteRecord(record);
            fusionTree.deleteRecord(record);
        }
        return removed;
    }

    public int getShardId() {
        return shardId;
    }

    public int getRecordCount() {
        return records.size();
    }

    public List<LogRecord> getRecords() {
        return records;
    }

    public synchronized void clear() {
        records.clear();
        trie.clear();
        fusionTree.clear();
    }
}
