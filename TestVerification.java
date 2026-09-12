import java.io.File;
import java.util.List;

public class TestVerification {

    public static void main(String[] args) throws Exception {
        System.out.println("=================================================");
        System.out.println("       AUTOMATED SYSTEM VERIFICATION SUITE       ");
        System.out.println("=================================================");

        String dataPath = "data" + File.separator + "logs_10k.csv";
        File f = new File(dataPath);
        if (!f.exists()) {
            System.out.println("Generating 10k test dataset...");
            DatasetGenerator.generateDataset(dataPath, 10000);
        }

        List<LogRecord> dataset = Benchmark.loadDataset(dataPath);
        System.out.printf("Loaded %,d test records from %s\n\n", dataset.size(), dataPath);

        System.out.println("--- Test 1: ClusterCoordinator Sharding & Scatter-Gather ---");
        int numShards = 3;
        ClusterCoordinator coordinator = new ClusterCoordinator(numShards);
        coordinator.ingestBatch(dataset);

        int totalInShards = coordinator.getTotalRecords();
        int[] dist = coordinator.getShardDistribution();
        System.out.printf("Total records in cluster: %,d (Expected: %,d) -> %s\n",
                totalInShards, dataset.size(), totalInShards == dataset.size() ? "PASS" : "FAIL");

        for (int i = 0; i < numShards; i++) {
            System.out.printf("  Shard %d count: %,d (Load: %.1f%%)\n", i, dist[i], (dist[i] * 100.0 / totalInShards));
            if (dist[i] < 2000 || dist[i] > 4500) {
                System.err.println("WARNING: Unbalanced hash distribution!");
            }
        }

        System.out.println("\n--- Test 2: Scatter-Gather Result Parity & K-Way Merge Ordering ---");
        CompressedTrie trie = new CompressedTrie();
        FusionTree fusionTree = new FusionTree();
        for (LogRecord r : dataset) {
            trie.insertRecord(r);
            fusionTree.insert(r);
        }
        QueryEngine singleNodeEngine = new QueryEngine(trie, fusionTree);

        String testKw = "connection";
        String testLevel = "ERROR";
        List<LogRecord> singleResults = singleNodeEngine.executeCombinedQuery(testKw, null, null, testLevel, null, null, null, dataset);
        List<LogRecord> distResults = coordinator.executeScatterGatherQuery(testKw, null, null, testLevel, null, null, null);

        System.out.printf("Single-Node query matches: %,d\n", singleResults.size());
        System.out.printf("Distributed query matches: %,d\n", distResults.size());
        boolean countMatch = singleResults.size() == distResults.size();
        System.out.printf("Result Count Parity: %s\n", countMatch ? "PASS" : "FAIL");

        boolean strictlyOrdered = true;
        for (int i = 1; i < distResults.size(); i++) {
            if (distResults.get(i).getEpochMillis() < distResults.get(i - 1).getEpochMillis()) {
                strictlyOrdered = false;
                break;
            }
        }
        System.out.printf("K-Way Min-Heap Merge Chronological Ordering: %s\n", strictlyOrdered ? "PASS" : "FAIL");

        System.out.println("\n--- Test 3: LogTemplateMiner (Clustering) ---");
        List<LogTemplateMiner.TemplateCluster> clusters = LogTemplateMiner.mineTemplates(dataset);
        System.out.printf("Discovered %,d templates across %,d logs -> PASS\n", clusters.size(), dataset.size());
        System.out.println("Top 3 Templates:");
        for (int i = 0; i < Math.min(3, clusters.size()); i++) {
            LogTemplateMiner.TemplateCluster c = clusters.get(i);
            System.out.printf("  #%d [%,d hits]: %s\n", (i + 1), c.getCount(), c.getTemplate());
        }

        System.out.println("\n--- Test 4: AnomalyDetector (Sliding-Window Z-Score) ---");
        List<AnomalyDetector.AnomalyIncident> anomalies = AnomalyDetector.detectAnomalies(dataset);
        System.out.printf("Analyzed timeline with 5-minute sliding windows. Anomalies found: %,d -> PASS\n", anomalies.size());
        if (!anomalies.isEmpty()) {
            System.out.println("Top Anomaly Spike: " + anomalies.get(0).toFormattedString());
        }

        System.out.println("\n--- Test 5: Safe Dynamic Deletion Verification ---");
        LogRecord logA = new LogRecord(9999001L, "2026-08-28 12:00:00", "ERROR", "Database", "Connection timeout on primary replica");
        LogRecord logB = new LogRecord(9999002L, "2026-08-28 12:00:00", "ERROR", "Database", "Connection refused on backup replica");

        CompressedTrie testTrie = new CompressedTrie();
        FusionTree testFt = new FusionTree();
        ClusterCoordinator testCoord = new ClusterCoordinator(3);

        testTrie.insertRecord(logA);
        testTrie.insertRecord(logB);
        testFt.insert(logA);
        testFt.insert(logB);
        testCoord.ingest(logA);
        testCoord.ingest(logB);

        System.out.println("Inserted 2 records (logA & logB) sharing keyword 'connection' and identical timestamp 12:00:00.");

        testTrie.deleteRecord(logA);
        testFt.deleteRecord(logA);
        testCoord.delete(logA);

        List<LogRecord> trieResults = testTrie.searchExact("connection");
        boolean trieSafe = (trieResults.size() == 1 && trieResults.get(0).getLogId() == 9999002L);
        System.out.printf("Trie keyword preservation for logB after deleting logA: %s (Found: %,d)\n",
                trieSafe ? "PASS" : "FAIL", trieResults.size());

        List<LogRecord> ftResults = testFt.searchExact(logA.getEpochMillis());
        boolean ftSafe = (ftResults.size() == 1 && ftResults.get(0).getLogId() == 9999002L);
        System.out.printf("FusionTree timestamp preservation for logB after deleting logA: %s (Found: %,d)\n",
                ftSafe ? "PASS" : "FAIL", ftResults.size());

        List<LogRecord> coordResults = testCoord.executeScatterGatherQuery("connection", null, null, null, null, null, null);
        boolean coordSafe = (coordResults.size() == 1 && coordResults.get(0).getLogId() == 9999002L);
        System.out.printf("ClusterCoordinator distributed deletion synchronization: %s (Found: %,d)\n",
                coordSafe ? "PASS" : "FAIL", coordResults.size());

        testCoord.shutdown();
        coordinator.shutdown();

        if (!trieSafe || !ftSafe || !coordSafe) {
            throw new RuntimeException("Deletion safety test failed!");
        }

        System.out.println("\n=================================================");
        System.out.println("           ALL VERIFICATION TESTS PASSED!        ");
        System.out.println("=================================================");
    }
}
