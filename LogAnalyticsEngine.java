import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class LogAnalyticsEngine {

    private static final Scanner scanner = new Scanner(System.in);
    private static final List<LogRecord> dataset = new ArrayList<>();
    private static final CompressedTrie singleNodeTrie = new CompressedTrie();
    private static final FusionTree singleNodeFusion = new FusionTree();
    private static final ClusterCoordinator coordinator = new ClusterCoordinator(3);

    // 1. COMPOUND QUERY ENGINE

    public static class QueryEngine {
        public static List<LogRecord> query(
                CompressedTrie trie, FusionTree fusion,
                String keyword, String prefix, String wildcard,
                Long startEpoch, Long endEpoch,
                String level, String service, List<LogRecord> allLogs) {

            Set<LogRecord> candidates = null;

            if (keyword != null && !keyword.isEmpty()) {
                candidates = new HashSet<>(trie.searchExact(keyword));
            } else if (prefix != null && !prefix.isEmpty()) {
                candidates = new HashSet<>(trie.searchPrefix(prefix));
            } else if (wildcard != null && !wildcard.isEmpty()) {
                candidates = new HashSet<>(trie.searchWildcard(wildcard));
            }

            if (startEpoch != null && endEpoch != null) {
                List<LogRecord> timeMatches = fusion.searchRange(startEpoch, endEpoch);
                if (candidates == null) {
                    candidates = new HashSet<>(timeMatches);
                } else {
                    candidates.retainAll(new HashSet<>(timeMatches));
                }
            }

            if (candidates == null) {
                candidates = new HashSet<>(allLogs);
            }

            List<LogRecord> result = new ArrayList<>();
            for (LogRecord r : candidates) {
                if (level != null && !level.equalsIgnoreCase("ALL") && !r.getLevel().equalsIgnoreCase(level))
                    continue;
                if (service != null && !service.equalsIgnoreCase("ALL") && !r.getService().equalsIgnoreCase(service))
                    continue;
                result.add(r);
            }

            result.sort(Comparator.comparingLong(LogRecord::getEpochMillis));
            return result;
        }
    }

    // 2. DISTRIBUTED SHARDING & MIN-HEAP K-WAY MERGE

    public static class ShardWorker {
        final int id;
        final List<LogRecord> records = new ArrayList<>();
        final CompressedTrie trie = new CompressedTrie();
        final FusionTree fusion = new FusionTree();

        ShardWorker(int id) {
            this.id = id;
        }

        synchronized void insert(LogRecord r) {
            records.add(r);
            trie.insertRecord(r);
            fusion.insert(r);
        }

        synchronized boolean delete(LogRecord r) {
            boolean removed = records.removeIf(rec -> rec.getLogId() == r.getLogId());
            trie.deleteRecord(r);
            fusion.delete(r);
            return removed;
        }

        List<LogRecord> searchLocal(String kw, String pfx, String wc, Long s, Long e, String lvl, String svc) {
            return QueryEngine.query(trie, fusion, kw, pfx, wc, s, e, lvl, svc, records);
        }
    }

    public static class ClusterCoordinator {
        private final int numShards;
        private final ShardWorker[] shards;
        private final ExecutorService threadPool;

        public ClusterCoordinator(int numShards) {
            this.numShards = numShards;
            this.shards = new ShardWorker[numShards];
            for (int i = 0; i < numShards; i++)
                shards[i] = new ShardWorker(i);
            this.threadPool = Executors.newFixedThreadPool(numShards, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                return t;
            });
        }

        public void ingest(LogRecord r) {
            int shardIdx = Math.floorMod(Long.hashCode(r.getLogId()), numShards);
            shards[shardIdx].insert(r);
        }

        public boolean delete(LogRecord r) {
            int shardIdx = Math.floorMod(Long.hashCode(r.getLogId()), numShards);
            return shards[shardIdx].delete(r);
        }

        public List<LogRecord> scatterGather(String kw, String pfx, String wc, Long s, Long e, String lvl, String svc) {
            List<CompletableFuture<List<LogRecord>>> futures = new ArrayList<>();
            for (ShardWorker worker : shards) {
                futures.add(CompletableFuture.supplyAsync(() -> worker.searchLocal(kw, pfx, wc, s, e, lvl, svc),
                        threadPool));
            }

            List<List<LogRecord>> shardResults = new ArrayList<>();
            for (CompletableFuture<List<LogRecord>> f : futures) {
                shardResults.add(f.join());
            }

            return kWayMerge(shardResults);
        }

        // Min-Heap K-Way Stream Merge O(M log S)
        private static List<LogRecord> kWayMerge(List<List<LogRecord>> streams) {
            class Cursor implements Comparable<Cursor> {
                final List<LogRecord> list;
                int idx = 0;

                Cursor(List<LogRecord> l) {
                    this.list = l;
                }

                LogRecord peek() {
                    return list.get(idx);
                }

                LogRecord next() {
                    return list.get(idx++);
                }

                boolean hasNext() {
                    return idx < list.size();
                }

                @Override
                public int compareTo(Cursor o) {
                    return Long.compare(this.peek().getEpochMillis(), o.peek().getEpochMillis());
                }
            }

            PriorityQueue<Cursor> minHeap = new PriorityQueue<>();
            int total = 0;
            for (List<LogRecord> list : streams) {
                if (list != null && !list.isEmpty()) {
                    minHeap.add(new Cursor(list));
                    total += list.size();
                }
            }

            List<LogRecord> merged = new ArrayList<>(total);
            while (!minHeap.isEmpty()) {
                Cursor c = minHeap.poll();
                merged.add(c.next());
                if (c.hasNext())
                    minHeap.add(c);
            }
            return merged;
        }

        public int[] getDistribution() {
            int[] d = new int[numShards];
            for (int i = 0; i < numShards; i++)
                d[i] = shards[i].records.size();
            return d;
        }
    }

    // 3. INTELLIGENT ANALYTICS (TOP-K HEAP, Z-SCORE, TEMPLATE MINER)

    public static class Analytics {

        // Top-K Heavy Hitters via Bounded Min-Heap O(N + U log K)
        public static List<Map.Entry<String, Integer>> getTopKErrors(List<LogRecord> logs, int k) {
            Map<String, Integer> freq = new HashMap<>();
            for (LogRecord r : logs) {
                if ("ERROR".equalsIgnoreCase(r.getLevel()) || "WARN".equalsIgnoreCase(r.getLevel())) {
                    String err = r.getService() + ": " + r.getMessage();
                    freq.put(err, freq.getOrDefault(err, 0) + 1);
                }
            }

            PriorityQueue<Map.Entry<String, Integer>> minHeap = new PriorityQueue<>(
                    Comparator.comparingInt(Map.Entry::getValue));

            for (Map.Entry<String, Integer> entry : freq.entrySet()) {
                if (minHeap.size() < k) {
                    minHeap.add(entry);
                } else if (entry.getValue() > minHeap.peek().getValue()) {
                    minHeap.poll();
                    minHeap.add(entry);
                }
            }

            List<Map.Entry<String, Integer>> res = new ArrayList<>(minHeap);
            res.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            return res;
        }

        // Sliding-Window Z-Score Statistical Anomaly Detector (5-minute windows)
        public static void detectAnomalies(List<LogRecord> logs, double zThreshold) {
            if (logs.size() < 10) {
                System.out.println("Insufficient data for statistical baseline.");
                return;
            }

            long minEpoch = Long.MAX_VALUE, maxEpoch = Long.MIN_VALUE;
            for (LogRecord r : logs) {
                minEpoch = Math.min(minEpoch, r.getEpochMillis());
                maxEpoch = Math.max(maxEpoch, r.getEpochMillis());
            }

            long windowMillis = 5 * 60 * 1000L;
            int numBuckets = (int) Math.ceil((double) (maxEpoch - minEpoch + 1) / windowMillis);
            if (numBuckets < 3)
                numBuckets = 3;

            int[] errorCounts = new int[numBuckets];
            for (LogRecord r : logs) {
                if ("ERROR".equalsIgnoreCase(r.getLevel())) {
                    int b = (int) ((r.getEpochMillis() - minEpoch) / windowMillis);
                    if (b >= 0 && b < numBuckets)
                        errorCounts[b]++;
                }
            }

            double sum = 0;
            for (int c : errorCounts)
                sum += c;
            double mean = sum / numBuckets;

            double varSum = 0;
            for (int c : errorCounts)
                varSum += Math.pow(c - mean, 2);
            double stdDev = Math.sqrt(varSum / (numBuckets - 1));

            System.out.printf("Timeline Baseline: %d windows | Mean: %.1f errors/window | StdDev: %.2f\n",
                    numBuckets, mean, stdDev);

            if (stdDev < 0.001) {
                System.out.println("No anomalies detected (variance is near zero).");
                return;
            }

            int anomalies = 0;
            for (int i = 0; i < numBuckets; i++) {
                double z = (errorCounts[i] - mean) / stdDev;
                if (z >= zThreshold) {
                    long wStart = minEpoch + (i * windowMillis);
                    long wEnd = wStart + windowMillis;
                    System.out.printf("  [ALERT] Spike detected [%s -> %s] Errors: %d | Z-Score: +%.2f\n",
                            LogRecord.formatEpoch(wStart), LogRecord.formatEpoch(wEnd), errorCounts[i], z);
                    anomalies++;
                }
            }
            if (anomalies == 0)
                System.out.println("All windows within normal statistical bounds.");
        }

        // Drain-Style Template Mining: Normalizes variable tokens into <*>
        public static void mineTemplates(List<LogRecord> logs) {
            Map<String, Integer> templateCounts = new HashMap<>();
            for (LogRecord r : logs) {
                String[] words = r.getMessage().split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (String w : words) {
                    String clean = w.replaceAll("[^a-zA-Z0-9]", "");
                    if (clean.matches("^\\d+$") || clean.matches("(?i)^0x[0-9a-f]+$") || clean.matches(".*\\d.*")) {
                        sb.append("<*> ");
                    } else {
                        sb.append(clean).append(" ");
                    }
                }
                String t = sb.toString().trim();
                templateCounts.put(t, templateCounts.getOrDefault(t, 0) + 1);
            }

            List<Map.Entry<String, Integer>> list = new ArrayList<>(templateCounts.entrySet());
            list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

            System.out.println("\nDiscovered Log Message Templates (Clustered):");
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                System.out.printf("  #%d [%,d occurrences]: %s\n", i + 1, list.get(i).getValue(), list.get(i).getKey());
            }
        }
    }

    // 4. BENCHMARK RUNNER

    public static void runBenchmark() {
        System.out.println("\n========================================================");
        System.out.println("              ALGORITHMIC BENCHMARK SUITE               ");
        System.out.println("========================================================");

        int n = dataset.size();
        if (n == 0) {
            System.out.println("Dataset is empty. Ingesting 10,000 synthetic logs...");
            generateSampleLogs(10000);
            n = dataset.size();
        }

        String kw = "connection";
        long t0 = System.nanoTime();
        int linearCount = 0;
        for (LogRecord r : dataset) {
            if (r.getMessage().toLowerCase().contains(kw))
                linearCount++;
        }
        long linearTimeNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        List<LogRecord> trieMatches = singleNodeTrie.searchExact(kw);
        long trieTimeNs = System.nanoTime() - t0;

        int mid = n / 2;
        long startE = dataset.get(Math.max(0, mid - 50)).getEpochMillis();
        long endE = dataset.get(Math.min(n - 1, mid + 50)).getEpochMillis();

        t0 = System.nanoTime();
        int linearTimeCount = 0;
        for (LogRecord r : dataset) {
            if (r.getEpochMillis() >= startE && r.getEpochMillis() <= endE)
                linearTimeCount++;
        }
        long linearTimeRangeNs = System.nanoTime() - t0;

        t0 = System.nanoTime();
        List<LogRecord> fusionMatches = singleNodeFusion.searchRange(startE, endE);
        long fusionTimeNs = System.nanoTime() - t0;

        double kwSpeedup = (double) linearTimeNs / Math.max(1, trieTimeNs);
        double rangeSpeedup = (double) linearTimeRangeNs / Math.max(1, fusionTimeNs);

        System.out.printf("\nDataset Size: %,d Records\n", n);
        System.out.println("----------------------------------------------------------------------------------");
        System.out.printf("%-30s | %-16s | %-16s | %-10s\n", "Operation", "Linear Baseline", "Proposed DSA", "Speedup");
        System.out.println("----------------------------------------------------------------------------------");
        System.out.printf("%-30s | %,13d ns | %,13d ns | %6.1fx\n", "Keyword Exact ('connection')", linearTimeNs,
                trieTimeNs, kwSpeedup);
        System.out.printf("%-30s | %,13d ns | %,13d ns | %6.1fx\n", "Timestamp Range Search", linearTimeRangeNs,
                fusionTimeNs, rangeSpeedup);
        System.out.println("----------------------------------------------------------------------------------");
    }

    // 5. INGESTION, DELETION & INTERACTIVE CLi

    public static void ingestSingleLog(LogRecord r) {
        dataset.add(r);
        singleNodeTrie.insertRecord(r);
        singleNodeFusion.insert(r);
        coordinator.ingest(r);
    }

    public static int ingestFromCsv(String filePath) {
        int count = 0;
        File file = new File(filePath);
        if (!file.exists()) {
            System.out.println("File does not exist: " + filePath);
            return 0;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            boolean first = true;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                if (first && (line.toLowerCase().contains("logid") || line.toLowerCase().contains("timestamp"))) {
                    first = false;
                    continue;
                }
                first = false;
                LogRecord r = LogRecord.fromCsvLine(line);
                if (r != null) {
                    ingestSingleLog(r);
                    count++;
                }
            }
        } catch (Exception e) {
            System.out.println("Error reading CSV file: " + e.getMessage());
        }
        return count;
    }

    public static boolean deleteLogById(long logId) {
        LogRecord target = null;
        for (LogRecord r : dataset) {
            if (r.getLogId() == logId) {
                target = r;
                break;
            }
        }
        if (target == null)
            return false;
        dataset.remove(target);
        singleNodeTrie.deleteRecord(target);
        singleNodeFusion.delete(target);
        coordinator.delete(target);
        return true;
    }

    private static void generateSampleLogs(int count) {
        String[] svcs = { "API", "Database", "Auth", "Payment", "Server" };
        String[] lvls = { "INFO", "WARN", "ERROR" };
        String[] msgs = {
                "Connection failed to replica", "Connection timeout on port 5432",
                "Authentication token expired", "Payment transaction completed",
                "High memory threshold exceeded", "Server worker thread started"
        };
        long baseEpoch = System.currentTimeMillis() - (count * 1000L);

        for (int i = 1; i <= count; i++) {
            long epoch = baseEpoch + (i * 1000L);
            LogRecord r = new LogRecord(
                    100000L + i,
                    LogRecord.formatEpoch(epoch),
                    lvls[i % lvls.length],
                    svcs[i % svcs.length],
                    msgs[i % msgs.length] + " host_" + (i % 10));
            ingestSingleLog(r);
        }
        System.out.printf("Ingested %,d sample logs successfully.\n", count);
    }

    public static void main(String[] args) {
        generateSampleLogs(1000);

        while (true) {
            System.out.println("\n========================================================");
            System.out.println("     INTELLIGENT DISTRIBUTED LOG ANALYTICS ENGINE       ");
            System.out.println("========================================================");
            System.out.printf("Active Logs: %,d | Cluster Shards: 3\n", dataset.size());
            System.out.println("1.  Exact Keyword Search (Compressed Trie)");
            System.out.println("2.  Prefix Search (Compressed Trie)");
            System.out.println("3.  Wildcard Search (Compressed Trie '*')");
            System.out.println("4.  Timestamp Range Search (Fusion Tree)");
            System.out.println("5.  Compound Query (Trie + Fusion Tree + Level + Service)");
            System.out.println("6.  Distributed Scatter-Gather (3 Shards + Min-Heap Merge)");
            System.out.println("7.  Top-K Frequent Errors (Bounded Min-Heap)");
            System.out.println("8.  Statistical Z-Score Anomaly Detection");
            System.out.println("9.  Log Template Mining (Parameter Masking)");
            System.out.println("10. View Cluster Topology & Shard Distribution");
            System.out.println("11. Run Algorithmic Benchmark (Linear vs Proposed)");
            System.out.println("12. Ingest a New Log Record (Interactive Real-Time)");
            System.out.println("13. Ingest Logs from CSV File");
            System.out.println("14. Delete Log Record by ID");
            System.out.println("15. Exit");
            System.out.print("Select choice [1-15]: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1":
                    System.out.print("Enter keyword: ");
                    List<LogRecord> r1 = singleNodeTrie.searchExact(scanner.nextLine().trim());
                    System.out.printf("Found %,d matches. First 3:\n", r1.size());
                    for (int i = 0; i < Math.min(3, r1.size()); i++)
                        System.out.println("  " + r1.get(i));
                    break;
                case "2":
                    System.out.print("Enter prefix: ");
                    List<LogRecord> r2 = singleNodeTrie.searchPrefix(scanner.nextLine().trim());
                    System.out.printf("Found %,d matches. First 3:\n", r2.size());
                    for (int i = 0; i < Math.min(3, r2.size()); i++)
                        System.out.println("  " + r2.get(i));
                    break;
                case "3":
                    System.out.print("Enter wildcard pattern (e.g. conn*): ");
                    List<LogRecord> r3 = singleNodeTrie.searchWildcard(scanner.nextLine().trim());
                    System.out.printf("Found %,d matches. First 3:\n", r3.size());
                    for (int i = 0; i < Math.min(3, r3.size()); i++)
                        System.out.println("  " + r3.get(i));
                    break;
                case "4":
                    if (dataset.isEmpty()) {
                        System.out.println("Dataset is empty.");
                        break;
                    }
                    long minD = dataset.get(0).getEpochMillis();
                    long maxD = dataset.get(dataset.size() - 1).getEpochMillis();
                    for (LogRecord rec : dataset) {
                        if (rec.getEpochMillis() < minD)
                            minD = rec.getEpochMillis();
                        if (rec.getEpochMillis() > maxD)
                            maxD = rec.getEpochMillis();
                    }
                    System.out.printf("Available dataset time range: [%s -> %s]\n",
                            LogRecord.formatEpoch(minD), LogRecord.formatEpoch(maxD));

                    System.out.print("Enter start timestamp (yyyy-MM-dd HH:mm:ss) or epoch ms (press Enter for min): ");
                    String startInput = scanner.nextLine().trim();
                    long sE = minD;
                    if (!startInput.isEmpty()) {
                        try {
                            sE = Long.parseLong(startInput);
                        } catch (NumberFormatException nfe) {
                            long parsed = LogRecord.parseEpoch(startInput);
                            if (parsed != 0L)
                                sE = parsed;
                            else
                                System.out.println("Could not parse start timestamp format. Using range min.");
                        }
                    }

                    System.out.print("Enter end timestamp (yyyy-MM-dd HH:mm:ss) or epoch ms (press Enter for max): ");
                    String endInput = scanner.nextLine().trim();
                    long eE = maxD;
                    if (!endInput.isEmpty()) {
                        try {
                            eE = Long.parseLong(endInput);
                        } catch (NumberFormatException nfe) {
                            long parsed = LogRecord.parseEpoch(endInput);
                            if (parsed != 0L)
                                eE = parsed;
                            else
                                System.out.println("Could not parse end timestamp format. Using range max.");
                        }
                    }

                    if (sE > eE) {
                        System.out.println("Start timestamp is after end timestamp. Swapping boundaries.");
                        long temp = sE;
                        sE = eE;
                        eE = temp;
                    }

                    System.out.printf("Searching window [%s -> %s]...\n", LogRecord.formatEpoch(sE),
                            LogRecord.formatEpoch(eE));
                    List<LogRecord> r4 = singleNodeFusion.searchRange(sE, eE);
                    System.out.printf("Found %,d range matches. First 3:\n", r4.size());
                    for (int i = 0; i < Math.min(3, r4.size()); i++)
                        System.out.println("  " + r4.get(i));
                    break;
                case "5":
                    System.out.print(
                            "Enter Keyword or Wildcard pattern (e.g. connection or conn*, press Enter to skip): ");
                    String textFilter = scanner.nextLine().trim();
                    String kw = null, wc = null;
                    if (!textFilter.isEmpty()) {
                        if (textFilter.contains("*")) {
                            wc = textFilter;
                        } else {
                            kw = textFilter;
                        }
                    }

                    System.out.print("Enter Severity Level [INFO / WARN / ERROR / ALL] (press Enter for ALL): ");
                    String lvlFilter = scanner.nextLine().trim();
                    if (lvlFilter.isEmpty())
                        lvlFilter = "ALL";

                    System.out.print("Enter Service (e.g. API, Database, Auth, Payment, Server, or Enter for ALL): ");
                    String svcFilter = scanner.nextLine().trim();
                    if (svcFilter.isEmpty())
                        svcFilter = "ALL";

                    System.out.print("Enter start timestamp (yyyy-MM-dd HH:mm:ss) or press Enter to skip: ");
                    String sInput = scanner.nextLine().trim();
                    Long compStart = null;
                    if (!sInput.isEmpty()) {
                        try {
                            compStart = Long.parseLong(sInput);
                        } catch (NumberFormatException nfe) {
                            long p = LogRecord.parseEpoch(sInput);
                            if (p != 0L)
                                compStart = p;
                            else
                                System.out
                                        .println("Could not parse start timestamp format. Skipping start time filter.");
                        }
                    }

                    System.out.print("Enter end timestamp (yyyy-MM-dd HH:mm:ss) or press Enter to skip: ");
                    String eInput = scanner.nextLine().trim();
                    Long compEnd = null;
                    if (!eInput.isEmpty()) {
                        try {
                            compEnd = Long.parseLong(eInput);
                        } catch (NumberFormatException nfe) {
                            long p = LogRecord.parseEpoch(eInput);
                            if (p != 0L)
                                compEnd = p;
                            else
                                System.out.println("Could not parse end timestamp format. Skipping end time filter.");
                        }
                    }

                    if (compStart != null && compEnd != null && compStart > compEnd) {
                        long tmp = compStart;
                        compStart = compEnd;
                        compEnd = tmp;
                    }

                    System.out.printf(
                            "\nExecuting Compound Query [Text='%s', Level='%s', Service='%s', TimeRange=%s]...\n",
                            (textFilter.isEmpty() ? "ANY" : textFilter),
                            lvlFilter,
                            svcFilter,
                            (compStart != null && compEnd != null
                                    ? "[" + LogRecord.formatEpoch(compStart) + " -> " + LogRecord.formatEpoch(compEnd)
                                            + "]"
                                    : "UNCONSTRAINED"));

                    List<LogRecord> r5 = QueryEngine.query(singleNodeTrie, singleNodeFusion, kw, null, wc, compStart,
                            compEnd, lvlFilter, svcFilter, dataset);
                    System.out.printf("Found %,d compound matches. First 5:\n", r5.size());
                    for (int i = 0; i < Math.min(5, r5.size()); i++)
                        System.out.println("  " + r5.get(i));
                    break;
                case "6":
                    System.out.print("Enter search term for distributed cluster: ");
                    List<LogRecord> r6 = coordinator.scatterGather(scanner.nextLine().trim(), null, null, null, null,
                            null, null);
                    System.out.printf("Scatter-Gather returned %,d chronologically merged results.\n", r6.size());
                    break;
                case "7":
                    System.out.print("Enter value of K: ");
                    int k = 3;
                    try {
                        String kInput = scanner.nextLine().trim();
                        if (!kInput.isEmpty()) {
                            k = Integer.parseInt(kInput);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid number. Defaulting to K=3.");
                        k = 3;
                    }
                    if (k <= 0) {
                        System.out.println("K must be positive. Defaulting to K=3.");
                        k = 3;
                    }
                    System.out.printf("\nTop-%d Error Culprits (Bounded Min-Heap):\n", k);
                    for (Map.Entry<String, Integer> e : Analytics.getTopKErrors(dataset, k)) {
                        System.out.printf("  [%,d occurrences] %s\n", e.getValue(), e.getKey());
                    }
                    break;
                case "8":
                    Analytics.detectAnomalies(dataset, 2.0);
                    break;
                case "9":
                    Analytics.mineTemplates(dataset);
                    break;
                case "10":
                    int[] dist = coordinator.getDistribution();
                    System.out.println("\nCluster Node Load Distribution:");
                    for (int i = 0; i < dist.length; i++) {
                        System.out.printf("  Shard %d: %,d records (%.1f%% load)\n", i, dist[i],
                                (dist[i] * 100.0 / Math.max(1, dataset.size())));
                    }
                    break;
                case "11":
                    runBenchmark();
                    break;
                case "12":
                    System.out.print("Enter Log ID (or press Enter for auto-id): ");
                    String idInput = scanner.nextLine().trim();
                    long newId = idInput.isEmpty() ? (System.currentTimeMillis() % 1000000L + 200000L)
                            : Long.parseLong(idInput);

                    System.out.print("Enter Level [INFO / WARN / ERROR] (default INFO): ");
                    String lvl = scanner.nextLine().trim().toUpperCase();
                    if (lvl.isEmpty())
                        lvl = "INFO";

                    System.out.print("Enter Service (e.g. Auth, Payment, API): ");
                    String svc = scanner.nextLine().trim();
                    if (svc.isEmpty())
                        svc = "API";

                    System.out.print("Enter Log Message: ");
                    String msg = scanner.nextLine().trim();

                    System.out.print("Enter Timestamp (yyyy-MM-dd HH:mm:ss) or press Enter for NOW: ");
                    String ts = scanner.nextLine().trim();
                    if (ts.isEmpty())
                        ts = LogRecord.formatEpoch(System.currentTimeMillis());

                    LogRecord newRec = new LogRecord(newId, ts, lvl, svc, msg);
                    ingestSingleLog(newRec);
                    int assignedShard = Math.floorMod(Long.hashCode(newRec.getLogId()), 3);
                    System.out.printf(
                            "\n[SUCCESS] Ingested record #%d into Shard %d, Compressed Trie, and Fusion Tree:\n  %s\n",
                            newId, assignedShard, newRec);
                    break;
                case "13":
                    System.out.print("Enter path to CSV file: ");
                    String csvPath = scanner.nextLine().trim();
                    int ingested = ingestFromCsv(csvPath);
                    System.out.printf("\n[SUCCESS] Ingested %,d records from CSV.\n", ingested);
                    break;
                case "14":
                    System.out.print("Enter Log ID to delete: ");
                    try {
                        long delId = Long.parseLong(scanner.nextLine().trim());
                        boolean deleted = deleteLogById(delId);
                        if (deleted) {
                            System.out.printf(
                                    "\n[SUCCESS] Log ID %d was successfully purged from dataset, Trie, Fusion Tree, and Cluster Shards.\n",
                                    delId);
                        } else {
                            System.out.printf("\n[NOT FOUND] Log ID %d does not exist in the active dataset.\n", delId);
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid numeric Log ID.");
                    }
                    break;
                case "15":
                    System.out.println("Exiting engine. Goodbye!");
                    return;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }
}