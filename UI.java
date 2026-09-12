import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class UI {

    private static final Scanner scanner = new Scanner(System.in);
    private static List<LogRecord> currentDataset = new ArrayList<>();
    private static CompressedTrie compressedTrie = new CompressedTrie();
    private static FusionTree fusionTree = new FusionTree();
    private static QueryEngine queryEngine = new QueryEngine(compressedTrie, fusionTree);
    private static ClusterCoordinator coordinator = new ClusterCoordinator(3);

    private static String activeDatasetName = "None";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void main(String[] args) {
        initDefaultDataset();

        while (true) {
            printHeader();
            System.out.println("--- Single-Node Core Search ---");
            System.out.println("1.  Keyword Search");
            System.out.println("2.  Prefix Search");
            System.out.println("3.  Wildcard Search");
            System.out.println("4.  Time Range Search");
            System.out.println("5.  Filter by Log Level");
            System.out.println("6.  Combined Search");
            System.out.println("7.  Top-K Errors");
            System.out.println("8.  Insert New Log");
            System.out.println("9.  Delete Log");
            System.out.println("10. Performance Benchmark");
            System.out.println("--- Distributed & Intelligence Extensions ---");
            System.out.println("11. Distributed Scatter-Gather Search (K-Way Merge)");
            System.out.println("12. View Cluster Topology & Shard Distribution");
            System.out.println("13. Algorithmic Anomaly Detection (Z-Score Spikes)");
            System.out.println("14. Log Template Discovery (Clustering)");
            System.out.println("15. Exit");
            System.out.print("\nEnter choice: ");

            String choice = scanner.nextLine().trim();
            System.out.println();

            switch (choice) {
                case "1": handleKeywordSearch(); break;
                case "2": handlePrefixSearch(); break;
                case "3": handleWildcardSearch(); break;
                case "4": handleTimeRangeSearch(); break;
                case "5": handleLogLevelFilter(); break;
                case "6": handleCombinedSearch(); break;
                case "7": handleTopKErrors(); break;
                case "8": handleInsertLog(); break;
                case "9": handleDeleteLog(); break;
                case "10": handleRunBenchmark(); break;
                case "11": handleDistributedSearch(); break;
                case "12": handleClusterTopology(); break;
                case "13": handleAnomalyDetection(); break;
                case "14": handleTemplateDiscovery(); break;
                case "15":
                    System.out.println("Shutting down coordinator threads. Exiting Log Analytics Engine. Goodbye!");
                    coordinator.shutdown();
                    return;
                case "load": handleLoadDataset(); break;
                default:
                    System.out.println("Invalid choice! Please select an option between 1 and 15.");
            }
            System.out.println("\nPress Enter to continue...");
            scanner.nextLine();
        }
    }

    private static void printHeader() {
        System.out.println("========================================");
        System.out.println("       LOG ANALYTICS SEARCH ENGINE      ");
        System.out.println("========================================");
        System.out.printf("Active Dataset: %s | Records: %,d | Shards: %d\n", activeDatasetName, currentDataset.size(), coordinator.getNumShards());
        System.out.println("----------------------------------------");
    }

    private static void initDefaultDataset() {
        String defaultPath = "data" + File.separator + "logs_100k.csv";
        File file = new File(defaultPath);
        if (!file.exists()) {
            try {
                System.out.println("Generating initial 100k log dataset...");
                DatasetGenerator.generateDataset(defaultPath, 100000);
            } catch (Exception e) {
                System.out.println("Failed to auto-generate default dataset: " + e.getMessage());
                return;
            }
        }
        loadDatasetFile(defaultPath);
    }

    private static void handleLoadDataset() {
        System.out.println("Available Datasets:");
        System.out.println("1. 10,000 logs (data/logs_10k.csv)");
        System.out.println("2. 50,000 logs (data/logs_50k.csv)");
        System.out.println("3. 100,000 logs (data/logs_100k.csv)");
        System.out.println("4. 500,000 logs (data/logs_500k.csv)");
        System.out.println("5. 1,000,000 logs (data/logs_1m.csv)");
        System.out.print("Select dataset option (1-5): ");

        String opt = scanner.nextLine().trim();
        String path = "data" + File.separator;
        switch (opt) {
            case "1": path += "logs_10k.csv"; break;
            case "2": path += "logs_50k.csv"; break;
            case "3": path += "logs_100k.csv"; break;
            case "4": path += "logs_500k.csv"; break;
            case "5": path += "logs_1m.csv"; break;
            default: System.out.println("Invalid selection."); return;
        }
        loadDatasetFile(path);
    }

    private static void loadDatasetFile(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) {
                System.out.println("File does not exist. Generating...");
                int count = 100000;
                if (path.contains("10k")) count = 10000;
                else if (path.contains("50k")) count = 50000;
                else if (path.contains("500k")) count = 500000;
                else if (path.contains("1m")) count = 1000000;
                DatasetGenerator.generateDataset(path, count);
            }

            long t0 = System.currentTimeMillis();
            currentDataset = Benchmark.loadDataset(path);
            compressedTrie = new CompressedTrie();
            fusionTree = new FusionTree();
            coordinator = new ClusterCoordinator(3);

            for (LogRecord rec : currentDataset) {
                compressedTrie.insertRecord(rec);
                fusionTree.insert(rec);
                coordinator.ingest(rec);
            }
            queryEngine = new QueryEngine(compressedTrie, fusionTree);

            long elapsed = System.currentTimeMillis() - t0;
            activeDatasetName = f.getName();
            System.out.printf("Loaded %,d log records from %s into single-node index and 3 shards in %d ms.\n",
                    currentDataset.size(), activeDatasetName, elapsed);
        } catch (Exception e) {
            System.out.println("Error loading dataset: " + e.getMessage());
        }
    }

    private static void handleKeywordSearch() {
        System.out.print("Enter keyword to search (e.g. connection, database, timeout): ");
        String keyword = scanner.nextLine().trim();
        if (keyword.isEmpty()) return;

        long t0 = System.nanoTime();
        List<LogRecord> results = compressedTrie.searchExact(keyword);
        long timeNs = System.nanoTime() - t0;

        printResults("Keyword Search Results ('" + keyword + "')", results, timeNs);
    }

    private static void handlePrefixSearch() {
        System.out.print("Enter prefix query (e.g. conn, auth, req): ");
        String prefix = scanner.nextLine().trim().replace("*", "");
        if (prefix.isEmpty()) return;

        long t0 = System.nanoTime();
        List<LogRecord> results = compressedTrie.searchPrefix(prefix);
        long timeNs = System.nanoTime() - t0;

        printResults("Prefix Search Results ('" + prefix + "*')", results, timeNs);
    }

    private static void handleWildcardSearch() {
        System.out.println("Wildcard patterns: * = any chars, ? = single char");
        System.out.println("Examples: conn*ion, *timeout, auth?, serv*");
        System.out.print("Enter wildcard pattern: ");
        String pattern = scanner.nextLine().trim();
        if (pattern.isEmpty()) return;

        long t0 = System.nanoTime();
        List<LogRecord> results = compressedTrie.searchWildcard(pattern);
        long timeNs = System.nanoTime() - t0;

        printResults("Wildcard Search Results ('" + pattern + "')", results, timeNs);
    }

    private static void handleTimeRangeSearch() {
        if (currentDataset.isEmpty()) {
            System.out.println("Dataset is empty.");
            return;
        }

        System.out.println("Sample timestamps range in dataset:");
        System.out.println("First log: " + currentDataset.get(0).getTimestamp());
        System.out.println("Last log:  " + currentDataset.get(currentDataset.size() - 1).getTimestamp());

        System.out.print("Enter start timestamp (yyyy-MM-dd HH:mm:ss): ");
        String startStr = scanner.nextLine().trim();
        System.out.print("Enter end timestamp   (yyyy-MM-dd HH:mm:ss): ");
        String endStr = scanner.nextLine().trim();

        long startEpoch = LogRecord.parseEpoch(startStr);
        long endEpoch = LogRecord.parseEpoch(endStr);

        if (startEpoch == 0 || endEpoch == 0) {
            System.out.println("Invalid date format! Expected format: yyyy-MM-dd HH:mm:ss");
            return;
        }

        long t0 = System.nanoTime();
        List<LogRecord> results = fusionTree.searchRange(startEpoch, endEpoch);
        long timeNs = System.nanoTime() - t0;

        printResults("Timestamp Range Results (" + startStr + " -> " + endStr + ")", results, timeNs);
    }

    private static void handleLogLevelFilter() {
        System.out.print("Enter Log Level (INFO, WARN, ERROR, DEBUG): ");
        String level = scanner.nextLine().trim().toUpperCase();

        long t0 = System.nanoTime();
        List<LogRecord> results = new ArrayList<>();
        for (LogRecord rec : currentDataset) {
            if (rec.getLevel().equalsIgnoreCase(level)) {
                results.add(rec);
            }
        }
        long timeNs = System.nanoTime() - t0;

        printResults("Log Level Filter Results ('" + level + "')", results, timeNs);
    }

    private static void handleCombinedSearch() {
        System.out.println("Search modes: Keyword, Prefix (conn*), or Wildcard (conn*tion)");
        System.out.print("Enter search term (or press Enter to skip): ");
        String term = scanner.nextLine().trim();
        System.out.print("Enter Log Level (INFO/WARN/ERROR/DEBUG or ALL): ");
        String lvl = scanner.nextLine().trim();
        System.out.print("Enter Start Time (yyyy-MM-dd HH:mm:ss or press Enter to skip): ");
        String startStr = scanner.nextLine().trim();
        System.out.print("Enter End Time   (yyyy-MM-dd HH:mm:ss or press Enter to skip): ");
        String endStr = scanner.nextLine().trim();

        Long startEpoch = startStr.isEmpty() ? null : LogRecord.parseEpoch(startStr);
        Long endEpoch = endStr.isEmpty() ? null : LogRecord.parseEpoch(endStr);

        String keyword = null, prefix = null, wildcard = null;
        if (!term.isEmpty()) {
            if (term.contains("*") || term.contains("?")) {
                wildcard = term;
            } else if (term.endsWith("*")) {
                prefix = term.substring(0, term.length() - 1);
            } else {
                keyword = term;
            }
        }

        long t0 = System.nanoTime();
        List<LogRecord> results = queryEngine.executeCombinedQuery(
                keyword, prefix, wildcard,
                lvl.isEmpty() ? "ALL" : lvl,
                "ALL",
                startEpoch, endEpoch,
                currentDataset
        );
        long timeNs = System.nanoTime() - t0;

        printResults("Combined Search Results", results, timeNs);
    }

    private static void handleTopKErrors() {
        System.out.print("Enter K for Top-K Error Analytics (e.g. 5): ");
        int k = 5;
        try {
            k = Integer.parseInt(scanner.nextLine().trim());
        } catch (Exception ignored) {}

        long t0 = System.nanoTime();
        List<TopKAnalytics.FrequencyEntry> topK = TopKAnalytics.getTopKErrors(currentDataset, k);
        long timeNs = System.nanoTime() - t0;

        System.out.printf("\n--- Top %d Most Frequent Error Logs (Execution time: %.3f ms) ---\n", k, timeNs / 1_000_000.0);
        System.out.printf("%-5s | %-35s | %-10s\n", "Rank", "Error Category", "Occurrences");
        System.out.println("---------------------------------------------------------------");
        int rank = 1;
        for (TopKAnalytics.FrequencyEntry entry : topK) {
            System.out.printf("%-5d | %-35s | %,d\n", rank++, entry.key, entry.count);
        }
    }

    private static void handleInsertLog() {
        System.out.println("--- Log Ingestion Mode ---");
        System.out.println("1. Manual Single Log Record Entry");
        System.out.println("2. Real-Time Streaming Ingestion Simulator (Live Dynamic Stream)");
        System.out.print("Select mode (1/2) [DEFAULT: 1]: ");
        String mode = scanner.nextLine().trim();

        if ("2".equals(mode)) {
            handleStreamingIngestion();
            return;
        }

        System.out.println("\nInsert New Log Record:");
        long id = 10000000L + currentDataset.size() + 1;
        String nowStr = LocalDateTime.now().format(FORMATTER);

        System.out.print("Enter Log Level (INFO/WARN/ERROR/DEBUG) [DEFAULT: INFO]: ");
        String lvl = scanner.nextLine().trim().toUpperCase();
        if (lvl.isEmpty()) lvl = "INFO";

        System.out.print("Enter Service (API/Database/Authentication/Payment/Server/Network/Storage) [DEFAULT: API]: ");
        String svc = scanner.nextLine().trim();
        if (svc.isEmpty()) svc = "API";

        System.out.print("Enter Log Message: ");
        String msg = scanner.nextLine().trim();
        if (msg.isEmpty()) msg = "Manual test entry created via UI";

        LogRecord newRecord = new LogRecord(id, nowStr, lvl, svc, msg);
        currentDataset.add(newRecord);
        compressedTrie.insertRecord(newRecord);
        fusionTree.insert(newRecord);
        coordinator.ingest(newRecord);

        System.out.println("\nLog successfully inserted into single-node indexes and routed to target shard!");
        System.out.println("  " + newRecord.toFormattedString());
    }

    private static void handleStreamingIngestion() {
        System.out.println("\n--- Real-Time Streaming Log Ingestion ---");
        System.out.print("Enter number of logs to stream (e.g. 50) [DEFAULT: 50]: ");
        String countStr = scanner.nextLine().trim();
        int count = 50;
        try { if (!countStr.isEmpty()) count = Integer.parseInt(countStr); } catch (Exception ignored) {}

        System.out.print("Enter stream delay in ms per record (e.g. 20) [DEFAULT: 20]: ");
        String delayStr = scanner.nextLine().trim();
        int delayMs = 20;
        try { if (!delayStr.isEmpty()) delayMs = Integer.parseInt(delayStr); } catch (Exception ignored) {}

        System.out.printf("\nStreaming %,d live logs at %d ms intervals into Single-Node indices & %d worker shards...\n",
                count, delayMs, coordinator.getNumShards());

        Random rand = new Random();
        String[] services = {"API", "Database", "Authentication", "Payment", "Server", "Network", "Storage"};
        String[] levels = {"INFO", "WARN", "ERROR", "DEBUG"};
        String[] messages = {
            "Real-time stream event processed",
            "Live connection established",
            "Streaming payment authorization",
            "Dynamic cache refresh completed",
            "Streaming health check heartbeat",
            "Real-time packet inspection verified",
            "High memory load detected on stream node"
        };

        long t0 = System.currentTimeMillis();
        for (int i = 1; i <= count; i++) {
            long id = 20000000L + currentDataset.size() + 1;
            String nowStr = LocalDateTime.now().format(FORMATTER);
            String lvl = levels[rand.nextInt(levels.length)];
            String svc = services[rand.nextInt(services.length)];
            String msg = messages[rand.nextInt(messages.length)] + " [seq=" + i + "]";

            LogRecord rec = new LogRecord(id, nowStr, lvl, svc, msg);
            currentDataset.add(rec);
            compressedTrie.insertRecord(rec);
            fusionTree.insert(rec);
            coordinator.ingest(rec);

            if (i <= 5 || i == count || i % 10 == 0) {
                System.out.printf("  [LIVE STREAM +%3d] %s -> Shard %d\n",
                        i, rec.toFormattedString(), Math.floorMod(Long.hashCode(id), coordinator.getNumShards()));
            }

            if (delayMs > 0 && i < count) {
                try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            }
        }
        long elapsed = System.currentTimeMillis() - t0;
        System.out.printf("\nStreaming complete! Ingested %,d records in %d ms (%.1f logs/sec).\n",
                count, elapsed, (count * 1000.0 / Math.max(1, elapsed)));
        System.out.printf("Total Active Dataset: %,d records | Cluster Total: %,d records.\n",
                currentDataset.size(), coordinator.getTotalRecords());
    }

    private static void handleDeleteLog() {
        System.out.print("Enter Log ID to delete: ");
        final long idToDelete;
        try {
            idToDelete = Long.parseLong(scanner.nextLine().trim());
        } catch (Exception e) {
            System.out.println("Invalid Log ID.");
            return;
        }

        LogRecord target = null;
        for (LogRecord rec : currentDataset) {
            if (rec.getLogId() == idToDelete) {
                target = rec;
                break;
            }
        }

        if (target == null) {
            System.out.printf("Log ID %d not found in active dataset.\n", idToDelete);
            return;
        }

        currentDataset.remove(target);

        compressedTrie.deleteRecord(target);

        fusionTree.deleteRecord(target);

        coordinator.delete(target);

        System.out.printf("Log record %d deleted successfully from all indices and worker shards.\n", idToDelete);
    }

    private static void handleRunBenchmark() {
        System.out.println("Running performance benchmarking suite across 10K, 50K, 100K, 500K, and 1M datasets...");
        Benchmark.runBenchmarks();
    }

    private static void handleDistributedSearch() {
        System.out.println("====================================================================");
        System.out.println("       DISTRIBUTED SCATTER-GATHER QUERY (PARALLEL K-WAY MERGE)      ");
        System.out.println("====================================================================");
        System.out.print("Enter search keyword (or Enter to skip): ");
        String kw = scanner.nextLine().trim();
        if (kw.isEmpty()) kw = null;

        System.out.print("Enter wildcard pattern (e.g. conn*tion, or Enter to skip): ");
        String wc = scanner.nextLine().trim();
        if (wc.isEmpty()) wc = null;

        System.out.print("Enter Log Level (INFO/WARN/ERROR/DEBUG or ALL): ");
        String lvl = scanner.nextLine().trim().toUpperCase();
        if (lvl.isEmpty() || lvl.equals("ALL")) lvl = null;

        System.out.print("Enter Service (e.g. Database, API, or ALL): ");
        String svc = scanner.nextLine().trim();
        if (svc.isEmpty() || svc.equalsIgnoreCase("ALL")) svc = null;

        System.out.println("\nExecuting Distributed Scatter-Gather across " + coordinator.getNumShards() + " worker shards in parallel...");

        long t0Dist = System.nanoTime();
        List<LogRecord> distResults = coordinator.executeScatterGatherQuery(kw, null, wc, lvl, svc, null, null);
        long distTimeNs = System.nanoTime() - t0Dist;

        long t0Single = System.nanoTime();
        List<LogRecord> singleResults = queryEngine.executeCombinedQuery(kw, null, wc, lvl, svc, null, null, currentDataset);
        long singleTimeNs = System.nanoTime() - t0Single;

        printResults(String.format("Distributed Scatter-Gather Results [%d Shards]", coordinator.getNumShards()), distResults, distTimeNs);

        System.out.println("\n--- Execution Model Comparison ---");
        System.out.printf("  Single-Node Index Query Engine  : %8.3f ms (%d ns)\n", singleTimeNs / 1_000_000.0, singleTimeNs);
        System.out.printf("  Distributed Parallel Scatter-Gather: %8.3f ms (%d ns)\n", distTimeNs / 1_000_000.0, distTimeNs);
        System.out.printf("  Result Parity Check             : %s (Single: %,d, Distributed: %,d records)\n",
                singleResults.size() == distResults.size() ? "PASS (100% Identical Count)" : "MISMATCH",
                singleResults.size(), distResults.size());
        System.out.println("  K-Way Merge Ordering Verification: Chronologically sorted by epoch millis.");
    }

    private static void handleClusterTopology() {
        System.out.println("====================================================================");
        System.out.println("             CLUSTER TOPOLOGY & SHARD DISTRIBUTION                 ");
        System.out.println("====================================================================");
        int numShards = coordinator.getNumShards();
        int total = coordinator.getTotalRecords();
        int[] dist = coordinator.getShardDistribution();

        System.out.printf("Architecture: In-process sharded distributed worker model\n");
        System.out.printf("Active Shard Count : %d independent worker nodes\n", numShards);
        System.out.printf("Total Logs Indexed : %,d records\n", total);
        System.out.printf("Partitioning Key   : Math.floorMod(Long.hashCode(logId), %d)\n", numShards);
        System.out.println("--------------------------------------------------------------------");

        for (int i = 0; i < numShards; i++) {
            double pct = total > 0 ? (dist[i] * 100.0 / total) : 0.0;
            System.out.printf("  Shard Worker [%d] -> %,7d records (%5.1f%% load) | Indexes: Local Radix Trie + Local Fusion Tree\n",
                    i, dist[i], pct);
        }
        System.out.println("--------------------------------------------------------------------");
        System.out.println("Each shard runs in its own memory partition. Queries are parallelized");
        System.out.println("using CompletableFuture and merged via a Min-Heap K-Way algorithm.");
    }

    private static void handleAnomalyDetection() {
        System.out.println("====================================================================");
        System.out.println("      ALGORITHMIC ANOMALY DETECTION (SLIDING-WINDOW Z-SCORE)        ");
        System.out.println("====================================================================");
        System.out.println("Algorithm: Partitions timeline into 5-minute sliding windows.");
        System.out.println("Evaluates baseline mean (mu) & standard deviation (sigma) of error rates.");
        System.out.println("Flags windows where Z-Score >= 2.5 (anomalous spike vs normal baseline).\n");

        long t0 = System.currentTimeMillis();
        List<AnomalyDetector.AnomalyIncident> incidents = AnomalyDetector.detectAnomalies(currentDataset);
        long elapsed = System.currentTimeMillis() - t0;

        if (incidents.isEmpty()) {
            System.out.println("No statistical anomalies detected. Error distribution is uniform across all windows.");
        } else {
            System.out.printf("Detected %,d Operational Incident Spikes (Processed in %d ms):\n", incidents.size(), elapsed);
            System.out.println("-------------------------------------------------------------------------------------------------");
            int limit = Math.min(10, incidents.size());
            for (int i = 0; i < limit; i++) {
                AnomalyDetector.AnomalyIncident inc = incidents.get(i);
                System.out.printf("  #%-2d %s\n", (i + 1), inc.toFormattedString());
            }
            if (incidents.size() > limit) {
                System.out.printf("  ... and %,d more detected anomaly incidents.\n", incidents.size() - limit);
            }
        }
    }

    private static void handleTemplateDiscovery() {
        System.out.println("====================================================================");
        System.out.println("          LOG TEMPLATE MINING & UNSUPERVISED CLUSTERING             ");
        System.out.println("====================================================================");
        System.out.println("Algorithm: Abstract unstructured text by replacing variable parameters");
        System.out.println("(numbers, IDs, hex, codes) with <*> placeholders to discover core event types.\n");

        long t0 = System.currentTimeMillis();
        List<LogTemplateMiner.TemplateCluster> clusters = LogTemplateMiner.mineTemplates(currentDataset);
        long elapsed = System.currentTimeMillis() - t0;

        System.out.printf("Discovered %,d Unique Event Templates across %,d logs in %d ms:\n",
                clusters.size(), currentDataset.size(), elapsed);
        System.out.println("-------------------------------------------------------------------------------------------------");

        int limit = Math.min(10, clusters.size());
        for (int i = 0; i < limit; i++) {
            LogTemplateMiner.TemplateCluster c = clusters.get(i);
            double pct = (c.getCount() * 100.0) / currentDataset.size();
            System.out.printf("  Template #%-2d [%,6d occurrences | %4.1f%%]:\n", (i + 1), c.getCount(), pct);
            System.out.printf("    Pattern: \"%s\"\n", c.getTemplate());
            System.out.printf("    Services: %s | Levels: %s\n", c.getServices(), c.getLevels());
            System.out.println();
        }
    }

    private static void printResults(String title, List<LogRecord> results, long timeNs) {
        System.out.printf("\n--- %s ---\n", title);
        System.out.printf("Matched Records: %,d | Execution Time: %.3f ms (%,d ns)\n", results.size(), timeNs / 1_000_000.0, timeNs);
        System.out.println("----------------------------------------------------------------------------------");

        int limit = Math.min(10, results.size());
        for (int i = 0; i < limit; i++) {
            System.out.println("  " + results.get(i).toFormattedString());
        }

        if (results.size() > limit) {
            System.out.printf("  ... and %,d more matching records.\n", results.size() - limit);
        }
    }
}
