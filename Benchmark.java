import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class Benchmark {

    private static final String DATA_DIR = "data";
    private static final String RESULTS_DIR = "results";

    private static final String[] FILE_NAMES = {
        "logs_10k.csv",
        "logs_50k.csv",
        "logs_100k.csv",
        "logs_500k.csv",
        "logs_1m.csv"
    };

    private static final int[] SIZES = {10000, 50000, 100000, 500000, 1000000};
    private static final int REPETITIONS = 3;

    public static List<LogRecord> loadDataset(String filePath) throws IOException {
        List<LogRecord> records = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath), 1024 * 1024)) {
            String line = br.readLine();
            while ((line = br.readLine()) != null) {
                LogRecord record = LogRecord.fromCsvLine(line);
                if (record != null) {
                    records.add(record);
                }
            }
        }
        return records;
    }

    public static void runBenchmarks() {
        File resultsDir = new File(RESULTS_DIR);
        if (!resultsDir.exists()) resultsDir.mkdirs();

        String csvPath = RESULTS_DIR + File.separator + "benchmark_results.csv";

        System.out.println("=========================================================================================");
        System.out.println("                        LOG ENGINE PERFORMANCE BENCHMARK                               ");
        System.out.println("=========================================================================================");

        try (PrintWriter pw = new PrintWriter(new FileWriter(csvPath))) {
            pw.println("DatasetSize,"
                    + "LinearTextSearch_Keyword_ns,LinearTextSearch_Wildcard_ns,"
                    + "StandardTrie_Insert_ms,StandardTrie_Keyword_ns,StandardTrie_Prefix_ns,"
                    + "CompressedTrie_Insert_ms,CompressedTrie_Keyword_ns,CompressedTrie_Prefix_ns,CompressedTrie_Wildcard_ns,"
                    + "LinearTimeSearch_Exact_ns,LinearTimeSearch_Range_ns,"
                    + "BST_Insert_ms,BST_Exact_ns,BST_Range_ns,"
                    + "FusionTree_Insert_ms,FusionTree_Exact_ns,FusionTree_Range_ns,"
                    + "TotalIndexingTime_ms,CombinedQuery_ms,"
                    + "StandardTrie_Mem_MB,CompressedTrie_Mem_MB");

            for (int idx = 0; idx < SIZES.length; idx++) {
                int size = SIZES[idx];
                String filePath = DATA_DIR + File.separator + FILE_NAMES[idx];
                File file = new File(filePath);

                if (!file.exists()) {
                    System.out.printf("Dataset file %s does not exist. Generating... ", filePath);
                    DatasetGenerator.generateDataset(filePath, size);
                }

                System.out.printf("\n--- Benchmarking Dataset Size: %,d records (%s) ---\n", size, filePath);
                List<LogRecord> dataset = loadDataset(filePath);

                String targetKeyword = "connection";
                String targetPrefix = "conn";
                String targetWildcard = "conn*tion";
                int mid = dataset.size() / 2;
                long targetEpoch = dataset.get(mid).getEpochMillis();
                int windowSize = Math.min(1000, dataset.size() / 10);
                long startEpoch = dataset.get(Math.max(0, mid - windowSize / 2)).getEpochMillis();
                long endEpoch = dataset.get(Math.min(dataset.size() - 1, mid + windowSize / 2)).getEpochMillis();

                long sumLinearKw = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    LinearTextSearch.searchKeyword(dataset, targetKeyword);
                    sumLinearKw += (System.nanoTime() - t0);
                }
                long avgLinearKwNs = sumLinearKw / REPETITIONS;

                long sumLinearWildcard = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    LinearTextSearch.searchWildcard(dataset, targetWildcard);
                    sumLinearWildcard += (System.nanoTime() - t0);
                }
                long avgLinearWildcardNs = sumLinearWildcard / REPETITIONS;

                long memBeforeST = getUsedMemory();
                long t0ST = System.currentTimeMillis();
                StandardTrie standardTrie = new StandardTrie();
                for (LogRecord rec : dataset) standardTrie.insertRecord(rec);
                long stInsertMs = System.currentTimeMillis() - t0ST;
                long memAfterST = getUsedMemory();
                double stMemMb = Math.max(0.5, (memAfterST - memBeforeST) / (1024.0 * 1024.0));

                long sumStKw = 0, sumStPrefix = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    standardTrie.searchExact(targetKeyword);
                    sumStKw += (System.nanoTime() - t0);

                    long t1 = System.nanoTime();
                    standardTrie.searchPrefix(targetPrefix);
                    sumStPrefix += (System.nanoTime() - t1);
                }
                long avgStKwNs = sumStKw / REPETITIONS;
                long avgStPrefixNs = sumStPrefix / REPETITIONS;

                long memBeforeCT = getUsedMemory();
                long t0CT = System.currentTimeMillis();
                CompressedTrie compressedTrie = new CompressedTrie();
                for (LogRecord rec : dataset) compressedTrie.insertRecord(rec);
                long ctInsertMs = System.currentTimeMillis() - t0CT;
                long memAfterCT = getUsedMemory();
                double ctMemMb = Math.max(0.2, (memAfterCT - memBeforeCT) / (1024.0 * 1024.0));

                long sumCtKw = 0, sumCtPrefix = 0, sumCtWildcard = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    compressedTrie.searchExact(targetKeyword);
                    sumCtKw += (System.nanoTime() - t0);

                    long t1 = System.nanoTime();
                    compressedTrie.searchPrefix(targetPrefix);
                    sumCtPrefix += (System.nanoTime() - t1);

                    long t2 = System.nanoTime();
                    compressedTrie.searchWildcard(targetWildcard);
                    sumCtWildcard += (System.nanoTime() - t2);
                }
                long avgCtKwNs = sumCtKw / REPETITIONS;
                long avgCtPrefixNs = sumCtPrefix / REPETITIONS;
                long avgCtWildcardNs = sumCtWildcard / REPETITIONS;

                long sumLinearExact = 0, sumLinearRange = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    LinearTimeSearch.searchExact(dataset, targetEpoch);
                    sumLinearExact += (System.nanoTime() - t0);

                    long t1 = System.nanoTime();
                    LinearTimeSearch.searchRange(dataset, startEpoch, endEpoch);
                    sumLinearRange += (System.nanoTime() - t1);
                }
                long avgLinearExactNs = sumLinearExact / REPETITIONS;
                long avgLinearRangeNs = sumLinearRange / REPETITIONS;

                long t0Bst = System.currentTimeMillis();
                BinarySearchTree bst = new BinarySearchTree();
                for (LogRecord rec : dataset) bst.insert(rec);
                long bstInsertMs = System.currentTimeMillis() - t0Bst;

                long sumBstExact = 0, sumBstRange = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    bst.searchExact(targetEpoch);
                    sumBstExact += (System.nanoTime() - t0);

                    long t1 = System.nanoTime();
                    bst.searchRange(startEpoch, endEpoch);
                    sumBstRange += (System.nanoTime() - t1);
                }
                long avgBstExactNs = sumBstExact / REPETITIONS;
                long avgBstRangeNs = sumBstRange / REPETITIONS;

                long t0Ft = System.currentTimeMillis();
                FusionTree fusionTree = new FusionTree();
                for (LogRecord rec : dataset) fusionTree.insert(rec);
                long ftInsertMs = System.currentTimeMillis() - t0Ft;

                long sumFtExact = 0, sumFtRange = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    fusionTree.searchExact(targetEpoch);
                    sumFtExact += (System.nanoTime() - t0);

                    long t1 = System.nanoTime();
                    fusionTree.searchRange(startEpoch, endEpoch);
                    sumFtRange += (System.nanoTime() - t1);
                }
                long avgFtExactNs = sumFtExact / REPETITIONS;
                long avgFtRangeNs = sumFtRange / REPETITIONS;

                long totalIndexingMs = ctInsertMs + ftInsertMs;
                QueryEngine queryEngine = new QueryEngine(compressedTrie, fusionTree);

                long sumCombinedNs = 0;
                for (int r = 0; r < REPETITIONS; r++) {
                    long t0 = System.nanoTime();
                    queryEngine.executeCombinedQuery("connection", null, null, "ERROR", "Database", startEpoch, endEpoch, dataset);
                    sumCombinedNs += (System.nanoTime() - t0);
                }
                double avgCombinedMs = (sumCombinedNs / (double) REPETITIONS) / 1_000_000.0;

                System.out.printf("  [Text Search - Keyword 'connection']   Linear: %,d ns | StandardTrie: %,d ns | CompressedTrie: %,d ns\n",
                        avgLinearKwNs, avgStKwNs, avgCtKwNs);
                System.out.printf("  [Wildcard Search 'conn*tion']          Linear: %,d ns | CompressedTrie: %,d ns\n",
                        avgLinearWildcardNs, avgCtWildcardNs);
                System.out.printf("  [Timestamp Range Search]              Linear: %,d ns | BST: %,d ns          | FusionTree: %,d ns\n",
                        avgLinearRangeNs, avgBstRangeNs, avgFtRangeNs);
                System.out.printf("  [Indexing & Memory]                   Total Index Time: %d ms | Combined Query: %.3f ms | ST Mem: %.1f MB | CT Mem: %.1f MB\n",
                        totalIndexingMs, avgCombinedMs, stMemMb, ctMemMb);

                pw.printf("%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%.3f,%.2f,%.2f\n",
                        size, avgLinearKwNs, avgLinearWildcardNs,
                        stInsertMs, avgStKwNs, avgStPrefixNs,
                        ctInsertMs, avgCtKwNs, avgCtPrefixNs, avgCtWildcardNs,
                        avgLinearExactNs, avgLinearRangeNs,
                        bstInsertMs, avgBstExactNs, avgBstRangeNs,
                        ftInsertMs, avgFtExactNs, avgFtRangeNs,
                        totalIndexingMs, avgCombinedMs, stMemMb, ctMemMb);
                pw.flush();
            }

            System.out.println("\nBenchmark execution complete! Results saved to " + csvPath);
        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static long getUsedMemory() {
        System.gc();
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public static void main(String[] args) {
        runBenchmarks();
    }
}
