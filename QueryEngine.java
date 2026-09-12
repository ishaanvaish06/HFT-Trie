import java.util.*;

public class QueryEngine {

    private final CompressedTrie trie;
    private final FusionTree fusionTree;

    public QueryEngine(CompressedTrie trie, FusionTree fusionTree) {
        this.trie = trie;
        this.fusionTree = fusionTree;
    }

    public List<LogRecord> executeCombinedQuery(
            String keyword,
            String prefix,
            String wildcardPattern,
            String logLevel,
            String service,
            Long startEpoch,
            Long endEpoch,
            List<LogRecord> masterDataset) {

        Set<LogRecord> candidates = null;

        if (keyword != null && !keyword.trim().isEmpty()) {
            candidates = new HashSet<>(trie.searchExact(keyword.trim()));
        } else if (prefix != null && !prefix.trim().isEmpty()) {
            candidates = new HashSet<>(trie.searchPrefix(prefix.trim()));
        } else if (wildcardPattern != null && !wildcardPattern.trim().isEmpty()) {
            candidates = new HashSet<>(trie.searchWildcard(wildcardPattern.trim()));
        }

        if (startEpoch != null && endEpoch != null) {
            List<LogRecord> rangeResults = fusionTree.searchRange(startEpoch, endEpoch);
            if (candidates == null) {
                candidates = new HashSet<>(rangeResults);
            } else {
                candidates.retainAll(new HashSet<>(rangeResults));
            }
        }

        if (candidates == null) {
            candidates = new HashSet<>(masterDataset);
        }

        List<LogRecord> finalResults = new ArrayList<>();
        for (LogRecord rec : candidates) {
            if (logLevel != null && !logLevel.equalsIgnoreCase("ALL") && !rec.getLevel().equalsIgnoreCase(logLevel)) {
                continue;
            }
            if (service != null && !service.equalsIgnoreCase("ALL") && !rec.getService().equalsIgnoreCase(service)) {
                continue;
            }
            finalResults.add(rec);
        }

        finalResults.sort(Comparator.comparingLong(LogRecord::getEpochMillis));
        return finalResults;
    }

    public List<LogRecord> executeCombinedQuery(
            String keyword,
            String prefix,
            String logLevel,
            String service,
            Long startEpoch,
            Long endEpoch,
            List<LogRecord> masterDataset) {
        return executeCombinedQuery(keyword, prefix, null, logLevel, service, startEpoch, endEpoch, masterDataset);
    }
}
