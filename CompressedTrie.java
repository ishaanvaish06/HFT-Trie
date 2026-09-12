import java.util.*;

public class CompressedTrie {

    private static class Node {
        Map<Character, Node> children = new HashMap<>(4);
        String edgeLabel = "";
        List<LogRecord> records = new ArrayList<>(8);
        boolean isEndOfWord = false;
    }

    private final Node root = new Node();
    private int size = 0;

    public static Set<String> extractTokens(LogRecord record) {
        Set<String> tokens = new HashSet<>();
        if (record.getService() != null && !record.getService().isEmpty()) {
            tokens.add(record.getService().toLowerCase().trim());
        }
        if (record.getLevel() != null && !record.getLevel().isEmpty()) {
            tokens.add(record.getLevel().toLowerCase().trim());
        }
        if (record.getMessage() != null) {
            String[] words = record.getMessage().toLowerCase().split("\\W+");
            for (String w : words) {
                if (!w.isEmpty()) {
                    tokens.add(w);
                }
            }
        }
        return tokens;
    }

    public void insertRecord(LogRecord record) {
        Set<String> tokens = extractTokens(record);
        for (String token : tokens) {
            insert(token, record);
        }
    }

    public boolean deleteRecord(LogRecord record) {
        if (record == null) return false;
        Set<String> tokens = extractTokens(record);
        boolean anyRemoved = false;
        for (String token : tokens) {
            if (removeRecordFromWord(token, record.getLogId())) {
                anyRemoved = true;
            }
        }
        return anyRemoved;
    }

    private boolean removeRecordFromWord(String key, long logId) {
        if (key == null || key.isEmpty()) return false;
        String lower = key.toLowerCase();
        Node leaf = findNode(root, lower, 0);
        if (leaf != null && leaf.isEndOfWord) {
            boolean removed = leaf.records.removeIf(r -> r.getLogId() == logId);
            if (removed && leaf.records.isEmpty()) {
                delete(lower);
            }
            return removed;
        }
        return false;
    }

    public void insert(String key, LogRecord record) {
        if (key == null || key.isEmpty()) return;
        String lower = key.toLowerCase();
        insertIntoNode(root, lower, 0, record);
    }

    private void insertIntoNode(Node node, String key, int keyIndex, LogRecord record) {
        if (keyIndex == key.length()) {
            if (!node.isEndOfWord) {
                node.isEndOfWord = true;
                size++;
            }
            node.records.add(record);
            return;
        }

        char ch = key.charAt(keyIndex);
        Node child = node.children.get(ch);

        if (child == null) {
            Node newNode = new Node();
            newNode.edgeLabel = key.substring(keyIndex);
            newNode.isEndOfWord = true;
            newNode.records.add(record);
            node.children.put(ch, newNode);
            size++;
            return;
        }

        String childLabel = child.edgeLabel;
        int matchLen = commonPrefixLength(key, keyIndex, childLabel, 0);

        if (matchLen == childLabel.length()) {
            insertIntoNode(child, key, keyIndex + matchLen, record);
            return;
        }

        Node splitNode = new Node();
        splitNode.edgeLabel = childLabel.substring(0, matchLen);
        node.children.put(ch, splitNode);

        child.edgeLabel = childLabel.substring(matchLen);
        splitNode.children.put(child.edgeLabel.charAt(0), child);

        if (keyIndex + matchLen == key.length()) {
            splitNode.isEndOfWord = true;
            splitNode.records.add(record);
            size++;
        } else {
            Node tailNode = new Node();
            tailNode.edgeLabel = key.substring(keyIndex + matchLen);
            tailNode.isEndOfWord = true;
            tailNode.records.add(record);
            splitNode.children.put(tailNode.edgeLabel.charAt(0), tailNode);
            size++;
        }
    }

    private int commonPrefixLength(String a, int aStart, String b, int bStart) {
        int len = 0;
        int maxLen = Math.min(a.length() - aStart, b.length() - bStart);
        while (len < maxLen && a.charAt(aStart + len) == b.charAt(bStart + len)) {
            len++;
        }
        return len;
    }

    public List<LogRecord> searchExact(String word) {
        if (word == null || word.isEmpty()) return Collections.emptyList();
        String lower = word.toLowerCase();
        Node leaf = findNode(root, lower, 0);
        if (leaf != null && leaf.isEndOfWord) {
            return new ArrayList<>(leaf.records);
        }
        return Collections.emptyList();
    }

    private Node findNode(Node node, String key, int keyIndex) {
        if (keyIndex >= key.length()) return node;

        char ch = key.charAt(keyIndex);
        Node child = node.children.get(ch);
        if (child == null) return null;

        String label = child.edgeLabel;
        int maxMatch = Math.min(label.length(), key.length() - keyIndex);

        for (int i = 0; i < maxMatch; i++) {
            if (key.charAt(keyIndex + i) != label.charAt(i)) {
                return null;
            }
        }

        if (maxMatch < label.length()) {

            return child;
        }

        return findNode(child, key, keyIndex + label.length());
    }

    public List<LogRecord> searchPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) return Collections.emptyList();
        String lower = prefix.toLowerCase();
        Node node = findNode(root, lower, 0);
        if (node == null) return Collections.emptyList();

        List<LogRecord> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        collectAllRecords(node, results, seenIds, 1000);
        return results;
    }

    public List<LogRecord> searchWildcard(String pattern) {
        if (pattern == null || pattern.isEmpty()) return Collections.emptyList();
        String lower = pattern.toLowerCase();
        List<LogRecord> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        wildcardSearchNode(root, lower, 0, results, seenIds, 1000);
        return results;
    }

    private void wildcardSearchNode(Node node, String pattern, int patIdx,
                                    List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (node == null || results.size() >= limit) return;

        if (patIdx >= pattern.length()) {
            collectAllRecords(node, results, seenIds, limit);
            return;
        }

        char pchar = pattern.charAt(patIdx);

        if (pchar == '*') {
            if (patIdx + 1 >= pattern.length()) {
                collectAllRecords(node, results, seenIds, limit);
                return;
            }
            String rest = pattern.substring(patIdx + 1);
            matchAfterStar(node, rest, results, seenIds, limit);
            return;
        }

        if (pchar == '?') {
            for (Node child : node.children.values()) {
                if (child.edgeLabel.isEmpty()) continue;
                wildcardMatchEdge(child, child.edgeLabel, 1, pattern, patIdx + 1, results, seenIds, limit);
            }
            return;
        }

        Node child = node.children.get(pchar);
        if (child != null) {
            wildcardMatchEdge(child, child.edgeLabel, 1, pattern, patIdx + 1, results, seenIds, limit);
        }
    }

    private void wildcardMatchEdge(Node node, String edgeLabel, int edgeIdx, String pattern, int patIdx,
                                   List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (results.size() >= limit) return;

        while (edgeIdx < edgeLabel.length()) {
            if (patIdx >= pattern.length()) {

                if (edgeIdx == edgeLabel.length()) {
                    collectAllRecords(node, results, seenIds, limit);
                }
                return;
            }

            char pc = pattern.charAt(patIdx);
            char ec = edgeLabel.charAt(edgeIdx);

            if (pc == '*') {
                if (patIdx + 1 >= pattern.length()) {
                    collectAllRecords(node, results, seenIds, limit);
                    return;
                }
                String rest = pattern.substring(patIdx + 1);
                matchAfterStarEdge(node, edgeLabel, edgeIdx, rest, results, seenIds, limit);
                return;
            }

            if (pc == '?') {
                edgeIdx++;
                patIdx++;
                continue;
            }

            if (pc != ec) return;

            edgeIdx++;
            patIdx++;
        }

        wildcardSearchNode(node, pattern, patIdx, results, seenIds, limit);
    }

    private void matchAfterStar(Node node, String rest, List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (node == null || results.size() >= limit) return;

        wildcardSearchNode(node, rest, 0, results, seenIds, limit);

        for (Node child : node.children.values()) {
            if (results.size() >= limit) return;
            matchAfterStar(child, rest, results, seenIds, limit);
        }
    }

    private void matchAfterStarEdge(Node node, String edgeLabel, int edgeIdx, String rest,
                                    List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (node == null || results.size() >= limit) return;

        if (edgeIdx < edgeLabel.length()) {
            wildcardMatchEdge(node, edgeLabel, edgeIdx, rest, 0, results, seenIds, limit);
            matchAfterStarEdge(node, edgeLabel, edgeIdx + 1, rest, results, seenIds, limit);
        } else {
            wildcardSearchNode(node, rest, 0, results, seenIds, limit);
            for (Node child : node.children.values()) {
                if (results.size() >= limit) return;
                matchAfterStar(child, rest, results, seenIds, limit);
            }
        }
    }

    private void collectAllRecords(Node node, List<LogRecord> results, Set<Long> seenIds, int limit) {
        if (node == null || results.size() >= limit) return;
        for (LogRecord rec : node.records) {
            if (seenIds.add(rec.getLogId())) {
                results.add(rec);
                if (results.size() >= limit) return;
            }
        }
        for (Node child : node.children.values()) {
            if (results.size() >= limit) return;
            collectAllRecords(child, results, seenIds, limit);
        }
    }

    public boolean delete(String key) {
        if (key == null || key.isEmpty()) return false;
        String lower = key.toLowerCase();
        boolean[] deleted = {false};
        deleteRec(root, lower, 0, deleted);
        if (deleted[0]) size--;
        return deleted[0];
    }

    private Node deleteRec(Node node, String key, int keyIndex, boolean[] deleted) {
        if (keyIndex == key.length()) {
            if (!node.isEndOfWord) return node;
            node.isEndOfWord = false;
            node.records.clear();
            deleted[0] = true;
            if (node.children.isEmpty()) return null;
            return node;
        }

        char ch = key.charAt(keyIndex);
        Node child = node.children.get(ch);
        if (child == null) return node;

        String label = child.edgeLabel;
        int matchLen = 0;
        while (matchLen < label.length() && keyIndex + matchLen < key.length()
                && label.charAt(matchLen) == key.charAt(keyIndex + matchLen)) {
            matchLen++;
        }

        if (matchLen < label.length()) return node;

        Node result = deleteRec(child, key, keyIndex + matchLen, deleted);
        if (result == null) {
            node.children.remove(ch);
            if (!node.isEndOfWord && node.children.size() == 1 && node != root) {
                Node only = node.children.values().iterator().next();
                node.edgeLabel = node.edgeLabel + only.edgeLabel;
                node.children = only.children;
                node.isEndOfWord = only.isEndOfWord;
                node.records = only.records;
            }
        } else if (result != child) {
            node.children.put(ch, result);
        }
        return node;
    }

    public int size() {
        return size;
    }

    public void clear() {
        root.children.clear();
        root.edgeLabel = "";
        root.records.clear();
        root.isEndOfWord = false;
        size = 0;
    }
}
