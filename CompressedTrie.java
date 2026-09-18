import java.util.*;

public class CompressedTrie {

    static class Node {
        String edgeLabel = "";
        boolean isEndOfWord = false;
        List<LogRecord> records = new ArrayList<>();
        Map<Character, Node> children = new HashMap<>();
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
                if (!w.isEmpty())
                    tokens.add(w);
            }
        }
        return tokens;
    }

    public void insertRecord(LogRecord record) {
        for (String token : extractTokens(record)) {
            insert(token, record);
        }
    }

    public void insert(String key, LogRecord record) {
        if (key == null || key.isEmpty())
            return;
        insertRec(root, key.toLowerCase(), 0, record);
    }

    private void insertRec(Node node, String key, int keyIndex, LogRecord record) {
        if (keyIndex == key.length()) {
            node.isEndOfWord = true;
            node.records.add(record);
            size++;
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

        int matchLen = commonPrefix(key, keyIndex, child.edgeLabel, 0);

        if (matchLen == child.edgeLabel.length()) {
            insertRec(child, key, keyIndex + matchLen, record);
        } else {

            Node middle = new Node();
            middle.edgeLabel = child.edgeLabel.substring(0, matchLen);
            node.children.put(ch, middle);

            child.edgeLabel = child.edgeLabel.substring(matchLen);
            middle.children.put(child.edgeLabel.charAt(0), child);

            if (keyIndex + matchLen == key.length()) {
                middle.isEndOfWord = true;
                middle.records.add(record);
            } else {
                Node tail = new Node();
                tail.edgeLabel = key.substring(keyIndex + matchLen);
                tail.isEndOfWord = true;
                tail.records.add(record);
                middle.children.put(tail.edgeLabel.charAt(0), tail);
            }
            size++;
        }
    }

    private int commonPrefix(String a, int aStart, String b, int bStart) {
        int len = 0;
        int max = Math.min(a.length() - aStart, b.length() - bStart);
        while (len < max && a.charAt(aStart + len) == b.charAt(bStart + len)) {
            len++;
        }
        return len;
    }

    public Node findExactNode(Node node, String word, int idx) {
        if (node == null)
            return null;
        if (idx == word.length())
            return node;
        char ch = word.charAt(idx);
        Node child = node.children.get(ch);
        if (child == null)
            return null;
        String label = child.edgeLabel;
        if (!word.startsWith(label, idx))
            return null;
        return findExactNode(child, word, idx + label.length());
    }

    public List<LogRecord> searchExact(String word) {
        if (word == null || word.isEmpty())
            return Collections.emptyList();
        Node node = findExactNode(root, word.toLowerCase(), 0);
        if (node != null && node.isEndOfWord) {
            return new ArrayList<>(node.records);
        }
        return Collections.emptyList();
    }

    public boolean deleteRecord(LogRecord record) {
        if (record == null)
            return false;
        boolean removedAny = false;
        for (String token : extractTokens(record)) {
            Node node = findExactNode(root, token.toLowerCase(), 0);
            if (node != null && node.isEndOfWord) {
                boolean removed = node.records.removeIf(r -> r.getLogId() == record.getLogId());
                if (removed) {
                    removedAny = true;
                    if (node.records.isEmpty()) {
                        node.isEndOfWord = false;
                    }
                }
            }
        }
        if (removedAny && size > 0)
            size--;
        return removedAny;
    }

    public List<LogRecord> searchPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty())
            return Collections.emptyList();
        Node node = findPrefixNode(root, prefix.toLowerCase(), 0);
        if (node == null)
            return Collections.emptyList();

        List<LogRecord> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        collectAll(node, results, seenIds);
        return results;
    }

    private Node findPrefixNode(Node node, String prefix, int idx) {
        if (idx >= prefix.length())
            return node;
        char ch = prefix.charAt(idx);
        Node child = node.children.get(ch);
        if (child == null)
            return null;

        String label = child.edgeLabel;
        int match = Math.min(label.length(), prefix.length() - idx);
        for (int i = 0; i < match; i++) {
            if (prefix.charAt(idx + i) != label.charAt(i))
                return null;
        }

        if (match < label.length() && idx + match == prefix.length()) {
            return child; // Prefix terminates inside this edge
        }
        return findPrefixNode(child, prefix, idx + label.length());
    }

    private void collectAll(Node node, List<LogRecord> results, Set<Long> seenIds) {
        if (node == null)
            return;
        for (LogRecord r : node.records) {
            if (seenIds.add(r.getLogId()))
                results.add(r);
        }
        for (Node child : node.children.values()) {
            collectAll(child, results, seenIds);
        }
    }

    public List<LogRecord> searchWildcard(String pattern) {
        List<LogRecord> results = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        if (pattern == null || pattern.isEmpty())
            return results;
        wildcardRec(root, pattern.toLowerCase(), 0, results, seenIds);
        return results;
    }

    private void wildcardRec(Node node, String pattern, int pIdx, List<LogRecord> results, Set<Long> seenIds) {
        if (node == null)
            return;
        if (pIdx >= pattern.length()) {
            if (node.isEndOfWord) {
                for (LogRecord r : node.records) {
                    if (seenIds.add(r.getLogId()))
                        results.add(r);
                }
            }
            return;
        }

        char pc = pattern.charAt(pIdx);
        if (pc == '*') {
            // Wildcard '*' matches empty or any sequence
            wildcardRec(node, pattern, pIdx + 1, results, seenIds);
            for (Node child : node.children.values()) {
                collectAll(child, results, seenIds);
            }
        } else {
            Node child = node.children.get(pc);
            if (child != null) {
                wildcardRec(child, pattern, pIdx + child.edgeLabel.length(), results, seenIds);
            }
        }
    }

    public int size() {
        return size;
    }
}