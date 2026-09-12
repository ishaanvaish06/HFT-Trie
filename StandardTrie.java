import java.util.*;

public class StandardTrie {

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>(4);
        List<LogRecord> records = new ArrayList<>(64);
        boolean isEndOfWord = false;
    }

    private final TrieNode root = new TrieNode();

    public void insert(String keyword, LogRecord record) {
        if (keyword == null || keyword.isEmpty()) return;
        TrieNode curr = root;
        String lower = keyword.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            curr = curr.children.computeIfAbsent(ch, c -> new TrieNode());
        }
        curr.isEndOfWord = true;
        curr.records.add(record);
    }

    public void insertRecord(LogRecord record) {
        String msg = record.getMessage().toLowerCase();
        String svc = record.getService().toLowerCase();
        String lvl = record.getLevel().toLowerCase();

        insert(svc, record);
        insert(lvl, record);
        for (String w : msg.split(" ")) {
            if (!w.isEmpty()) insert(w, record);
        }
    }

    public List<LogRecord> searchExact(String word) {
        if (word == null || word.isEmpty()) return Collections.emptyList();
        TrieNode curr = root;
        String lower = word.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            curr = curr.children.get(ch);
            if (curr == null) return Collections.emptyList();
        }
        return curr.isEndOfWord ? new ArrayList<>(curr.records) : Collections.emptyList();
    }

    public List<LogRecord> searchPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        TrieNode curr = root;
        String lower = prefix.toLowerCase();
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            curr = curr.children.get(ch);
            if (curr == null) return Collections.emptyList();
        }

        List<LogRecord> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        collectAllRecords(curr, results, seenIds, 1000);
        return results;
    }

    private void collectAllRecords(TrieNode node, List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (node == null || results.size() >= limit) return;
        for (LogRecord rec : node.records) {
            if (seenIds.add(rec.getLogId())) {
                results.add(rec);
                if (results.size() >= limit) return;
            }
        }
        for (TrieNode child : node.children.values()) {
            if (results.size() >= limit) return;
            collectAllRecords(child, results, seenIds, limit);
        }
    }
}
