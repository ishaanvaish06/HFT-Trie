import java.util.*;

/**
 * Fusion Tree (Multi-Way Word-RAM Temporal Index)
 * Branching factor B=8; indexes 64-bit millisecond timestamps.
 * Uses Fredman-Willard bitwise distinguishing bit extraction and multi-way range pruning.
 */
@SuppressWarnings("unchecked")
public class FusionTree {

    private static final int B = 8; // Branching factor

    static class Node {
        boolean isLeaf;
        int count = 0;
        long[] keys = new long[B];
        List<LogRecord>[] records = new List[B];
        Node[] children;
        int[] distinguishingBits = new int[0];

        Node(boolean isLeaf) {
            this.isLeaf = isLeaf;
            this.children = isLeaf ? null : new Node[B + 1];
        }

        // Fredman-Willard bitwise distinguishing bits extraction via XOR
        void updateDistinguishingBits() {
            if (count <= 1) {
                distinguishingBits = new int[0];
                return;
            }
            boolean[] diff = new boolean[64];
            int diffCount = 0;
            for (int i = 0; i < count - 1; i++) {
                long xor = keys[i] ^ keys[i + 1];
                if (xor != 0) {
                    int bitPos = 63 - Long.numberOfLeadingZeros(xor);
                    if (!diff[bitPos]) {
                        diff[bitPos] = true;
                        diffCount++;
                    }
                }
            }
            distinguishingBits = new int[diffCount];
            int idx = 0;
            for (int i = 0; i < 64; i++) {
                if (diff[i]) distinguishingBits[idx++] = i;
            }
        }

        // Sketch function: extracts distinguishing bit positions into a compact word
        long sketch(long key) {
            long s = 0;
            for (int i = 0; i < distinguishingBits.length; i++) {
                long bit = (key >> distinguishingBits[i]) & 1L;
                s |= (bit << i);
            }
            return s;
        }

        int findKeyIndex(long key) {
            int l = 0, r = count - 1;
            while (l <= r) {
                int mid = (l + r) >>> 1;
                if (keys[mid] == key) return mid;
                if (keys[mid] < key) l = mid + 1;
                else r = mid - 1;
            }
            return -1;
        }

        int findChildIndex(long key) {
            int i = 0;
            while (i < count && key > keys[i]) i++;
            return i;
        }
    }

    private Node root = new Node(true);

    public void insert(LogRecord record) {
        long key = record.getEpochMillis();
        Node r = root;
        if (r.count == B) {
            Node s = new Node(false);
            root = s;
            s.children[0] = r;
            splitChild(s, 0, r);
            insertNonFull(s, key, record);
        } else {
            insertNonFull(r, key, record);
        }
    }

    private void insertNonFull(Node node, long key, LogRecord record) {
        if (node.isLeaf) {
            int idx = node.findKeyIndex(key);
            if (idx != -1) {
                node.records[idx].add(record);
                return;
            }
            int i = node.count - 1;
            while (i >= 0 && node.keys[i] > key) {
                node.keys[i + 1] = node.keys[i];
                node.records[i + 1] = node.records[i];
                i--;
            }
            node.keys[i + 1] = key;
            node.records[i + 1] = new ArrayList<>();
            node.records[i + 1].add(record);
            node.count++;
            node.updateDistinguishingBits();
        } else {
            int i = node.findChildIndex(key);
            if (node.children[i].count == B) {
                splitChild(node, i, node.children[i]);
                if (key > node.keys[i]) i++;
            }
            insertNonFull(node.children[i], key, record);
        }
    }

    private void splitChild(Node parent, int i, Node fullChild) {
        int mid = B / 2;
        Node z = new Node(fullChild.isLeaf);
        z.count = B - mid - 1;

        for (int j = 0; j < z.count; j++) {
            z.keys[j] = fullChild.keys[j + mid + 1];
            z.records[j] = fullChild.records[j + mid + 1];
        }

        if (!fullChild.isLeaf) {
            for (int j = 0; j <= z.count; j++) {
                z.children[j] = fullChild.children[j + mid + 1];
            }
        }

        long promotedKey = fullChild.keys[mid];
        List<LogRecord> promotedRecords = fullChild.records[mid];
        fullChild.count = mid;

        for (int j = parent.count; j >= i + 1; j--) {
            parent.children[j + 1] = parent.children[j];
        }
        parent.children[i + 1] = z;

        for (int j = parent.count - 1; j >= i; j--) {
            parent.keys[j + 1] = parent.keys[j];
            parent.records[j + 1] = parent.records[j];
        }
        parent.keys[i] = promotedKey;
        parent.records[i] = promotedRecords;
        parent.count++;

        fullChild.updateDistinguishingBits();
        z.updateDistinguishingBits();
        parent.updateDistinguishingBits();
    }

    public List<LogRecord> searchExact(long epochMillis) {
        return searchNode(root, epochMillis);
    }

    private List<LogRecord> searchNode(Node node, long key) {
        if (node == null) return Collections.emptyList();
        int idx = node.findKeyIndex(key);
        if (idx != -1) return new ArrayList<>(node.records[idx]);
        if (node.isLeaf) return Collections.emptyList();
        return searchNode(node.children[node.findChildIndex(key)], key);
    }

    public boolean delete(LogRecord record) {
        if (record == null) return false;
        return deleteFromNode(root, record.getEpochMillis(), record.getLogId());
    }

    private boolean deleteFromNode(Node node, long key, long logId) {
        if (node == null) return false;
        int idx = node.findKeyIndex(key);
        if (idx != -1) {
            boolean removed = node.records[idx].removeIf(r -> r.getLogId() == logId);
            if (removed) {
                if (node.records[idx].isEmpty()) {
                    if (node.isLeaf) {
                        for (int j = idx; j < node.count - 1; j++) {
                            node.keys[j] = node.keys[j + 1];
                            node.records[j] = node.records[j + 1];
                        }
                        node.keys[node.count - 1] = 0;
                        node.records[node.count - 1] = null;
                        node.count--;
                        node.updateDistinguishingBits();
                    }
                }
                return true;
            }
            return false;
        }
        if (node.isLeaf) return false;
        int childIdx = node.findChildIndex(key);
        return deleteFromNode(node.children[childIdx], key, logId);
    }

    public List<LogRecord> searchRange(long start, long end) {
        List<LogRecord> results = new ArrayList<>();
        rangeRec(root, start, end, results);
        return results;
    }

    private void rangeRec(Node node, long start, long end, List<LogRecord> results) {
        if (node == null) return;
        int i = 0;
        while (i < node.count) {
            if (!node.isLeaf && start <= node.keys[i]) {
                rangeRec(node.children[i], start, end, results);
            }
            if (node.keys[i] >= start && node.keys[i] <= end) {
                results.addAll(node.records[i]);
            }
            if (node.keys[i] > end) break;
            i++;
        }
        if (!node.isLeaf && node.keys[node.count - 1] <= end) {
            rangeRec(node.children[node.count], start, end, results);
        }
    }
}