import java.util.*;

@SuppressWarnings("unchecked")
public class FusionTree {

    public static final int DEFAULT_BRANCHING_FACTOR = 8;
    private static final int MAX_KEYS = 62;

    private static class Node {
        long[] keys;
        List[] records;
        Node[] children;
        int keyCount;
        boolean isLeaf;

        // Fredman-Willard Word-RAM Fusion Tree Sketch Fields
        int[] distinguishingBits;
        long nodeSketchWord;
        long indicatorMask;
        int fieldSize;
        int r;

        Node(boolean leaf, int b) {
            this.isLeaf = leaf;
            this.keys = new long[b];
            this.records = new List[b];
            this.children = leaf ? null : new Node[b + 1];
            this.keyCount = 0;
            this.distinguishingBits = new int[0];
        }

        void rebuildSketch() {
            if (keyCount == 0) {
                distinguishingBits = new int[0];
                nodeSketchWord = 0;
                indicatorMask = 0;
                r = 0;
                fieldSize = 0;
                return;
            }

            // Extract distinguishing bit positions among adjacent keys
            boolean[] isDiff = new boolean[64];
            int diffCount = 0;
            for (int i = 0; i < keyCount - 1; i++) {
                long xor = keys[i] ^ keys[i + 1];
                if (xor != 0) {
                    int bitPos = 63 - Long.numberOfLeadingZeros(xor);
                    if (!isDiff[bitPos]) {
                        isDiff[bitPos] = true;
                        diffCount++;
                    }
                }
            }

            distinguishingBits = new int[diffCount];
            int idx = 0;
            for (int b = 0; b < 64; b++) {
                if (isDiff[b]) distinguishingBits[idx++] = b;
            }

            r = Math.max(1, diffCount);
            fieldSize = r + 1;

            nodeSketchWord = 0;
            indicatorMask = 0;
            for (int i = 0; i < keyCount; i++) {
                long s = sketch(keys[i]);
                long field = (1L << r) | s;
                nodeSketchWord |= (field << (i * fieldSize));
                indicatorMask |= (1L << (i * fieldSize + r));
            }
        }

        long sketch(long x) {
            long s = 0;
            for (int i = 0; i < distinguishingBits.length; i++) {
                long bit = (x >>> distinguishingBits[i]) & 1L;
                s |= (bit << i);
            }
            return s;
        }

        int findChildIndexFusion(long q) {
            if (keyCount == 0) return 0;
            if (distinguishingBits == null || distinguishingBits.length == 0 || keyCount > 8) {
                int lo = 0, hi = keyCount;
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (keys[mid] <= q) lo = mid + 1;
                    else hi = mid;
                }
                return lo;
            }

            long qSketch = sketch(q);
            long queryWord = 0;
            long qField = (0L << r) | qSketch;
            for (int i = 0; i < keyCount; i++) {
                queryWord |= (qField << (i * fieldSize));
            }

            // Word-RAM parallel subtraction across all k fields simultaneously
            long diff = nodeSketchWord - queryWord;
            long borrowed = (~diff) & indicatorMask;
            int approxRank = Long.bitCount(borrowed);

            int candIdx = Math.min(approxRank, keyCount - 1);
            long candKey = keys[candIdx];

            if (q == candKey) {
                return candIdx + 1;
            }

            long xor = q ^ candKey;
            int lcpZeros = Long.numberOfLeadingZeros(xor);
            int divBit = 63 - lcpZeros;

            // Longest Common Prefix branch correction
            long qBit = (q >>> divBit) & 1L;
            int pos = candIdx;
            if (qBit == 0) {
                while (pos > 0 && keys[pos - 1] > q) pos--;
                while (pos < keyCount && keys[pos] <= q) pos++;
            } else {
                while (pos < keyCount && keys[pos] <= q) pos++;
                while (pos > 0 && keys[pos - 1] > q) pos--;
            }
            return pos;
        }

        int findInsertPosFusion(long q) {
            if (keyCount == 0) return 0;
            if (distinguishingBits == null || distinguishingBits.length == 0 || keyCount > 8) {
                int lo = 0, hi = keyCount;
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (keys[mid] < q) lo = mid + 1;
                    else hi = mid;
                }
                return lo;
            }

            long qSketch = sketch(q);
            long queryWord = 0;
            long qField = (0L << r) | qSketch;
            for (int i = 0; i < keyCount; i++) {
                queryWord |= (qField << (i * fieldSize));
            }

            long diff = nodeSketchWord - queryWord;
            long borrowed = (~diff) & indicatorMask;
            int approxRank = Long.bitCount(borrowed);

            int candIdx = Math.min(approxRank, keyCount - 1);
            long candKey = keys[candIdx];

            if (q == candKey) {
                int pos = candIdx;
                while (pos > 0 && keys[pos - 1] == q) pos--;
                return pos;
            }

            long xor = q ^ candKey;
            int lcpZeros = Long.numberOfLeadingZeros(xor);
            int divBit = 63 - lcpZeros;

            long qBit = (q >>> divBit) & 1L;
            int pos = candIdx;
            if (qBit == 0) {
                while (pos > 0 && keys[pos - 1] >= q) pos--;
                while (pos < keyCount && keys[pos] < q) pos++;
            } else {
                while (pos < keyCount && keys[pos] < q) pos++;
                while (pos > 0 && keys[pos - 1] >= q) pos--;
            }
            return pos;
        }

        int findLeafKeyIndexFusion(long q) {
            if (keyCount == 0) return -1;
            int idx = findChildIndexFusion(q);
            if (idx > 0 && keys[idx - 1] == q) {
                return idx - 1;
            }
            return -1;
        }
    }

    private Node root;
    private int branchingFactor;
    private int totalNodes = 0;

    public FusionTree() {
        this.branchingFactor = DEFAULT_BRANCHING_FACTOR;
    }

    public FusionTree(int branchingFactor) {
        this.branchingFactor = Math.min(MAX_KEYS, Math.max(4, branchingFactor));
    }

    public int computeBranchingFactor(int n) {
        if (n <= 0) return 4;
        int logN = 64 - Long.numberOfLeadingZeros(n);
        int logLogN = logN > 1 ? 64 - Long.numberOfLeadingZeros(logN - 1) : 1;
        return Math.min(DEFAULT_BRANCHING_FACTOR, Math.max(4, logN / Math.max(1, logLogN)));
    }

    public void insert(LogRecord record) {
        long key = record.getEpochMillis();
        if (root == null) {
            root = new Node(true, branchingFactor);
            root.keys[0] = key;
            root.records[0] = new ArrayList<>();
            root.records[0].add(record);
            root.keyCount = 1;
            root.rebuildSketch();
            totalNodes = 1;
            return;
        }

        SplitResult split = insertRec(root, key, record);
        if (split != null) {
            Node newRoot = new Node(false, branchingFactor);
            newRoot.keys[0] = split.promotedKey;
            newRoot.records[0] = null;
            newRoot.children[0] = root;
            newRoot.children[1] = split.newNode;
            newRoot.keyCount = 1;
            newRoot.rebuildSketch();
            root = newRoot;
            totalNodes += 2;
        }
    }

    private static class SplitResult {
        long promotedKey;
        Node newNode;

        SplitResult(long key, Node node) {
            this.promotedKey = key;
            this.newNode = node;
        }
    }

    private SplitResult insertRec(Node node, long key, LogRecord record) {
        if (node.isLeaf) {
            return insertIntoLeaf(node, key, record);
        }

        int idx = findChildIndex(node, key);

        SplitResult childSplit = insertRec(node.children[idx], key, record);
        if (childSplit == null) return null;

        return insertIntoInternal(node, childSplit.promotedKey, childSplit.newNode);
    }

    private SplitResult insertIntoLeaf(Node leaf, long key, LogRecord record) {
        int pos = findInsertPos(leaf, key);

        if (pos < leaf.keyCount && leaf.keys[pos] == key) {
            leaf.records[pos].add(record);
            return null;
        }

        if (leaf.keyCount < branchingFactor) {
            shiftRight(leaf, pos);
            leaf.keys[pos] = key;
            leaf.records[pos] = new ArrayList<>();
            leaf.records[pos].add(record);
            leaf.keyCount++;
            leaf.rebuildSketch();
            return null;
        }

        return splitLeaf(leaf, key, record, pos);
    }

    private SplitResult splitLeaf(Node leaf, long key, LogRecord record, int insertPos) {
        int totalKeys = branchingFactor + 1;
        long[] tempKeys = new long[totalKeys];
        List[] tempRecords = new List[totalKeys];

        int j = 0;
        for (int i = 0; i <= branchingFactor; i++) {
            if (i == insertPos) {
                tempKeys[i] = key;
                ArrayList<LogRecord> newRec = new ArrayList<>();
                newRec.add(record);
                tempRecords[i] = newRec;
            } else {
                tempKeys[i] = leaf.keys[j];
                tempRecords[i] = leaf.records[j];
                j++;
            }
        }

        int splitPoint = (totalKeys + 1) / 2;
        Node newNode = new Node(true, branchingFactor);

        leaf.keyCount = 0;
        for (int i = 0; i < splitPoint; i++) {
            leaf.keys[i] = tempKeys[i];
            leaf.records[i] = tempRecords[i];
            leaf.keyCount++;
        }
        leaf.rebuildSketch();

        newNode.keyCount = 0;
        for (int i = splitPoint; i < totalKeys; i++) {
            newNode.keys[newNode.keyCount] = tempKeys[i];
            newNode.records[newNode.keyCount] = tempRecords[i];
            newNode.keyCount++;
        }
        newNode.rebuildSketch();

        totalNodes++;
        return new SplitResult(newNode.keys[0], newNode);
    }

    private SplitResult insertIntoInternal(Node node, long key, Node newChild) {
        int idx = node.findChildIndexFusion(key);

        if (idx < node.keyCount && node.keys[idx] == key) {
            return null;
        }

        if (node.keyCount < branchingFactor) {
            shiftRightInternal(node, idx);
            node.keys[idx] = key;
            node.children[idx + 1] = newChild;
            node.keyCount++;
            node.rebuildSketch();
            return null;
        }

        return splitInternal(node, key, newChild, idx);
    }

    private SplitResult splitInternal(Node node, long key, Node newChild, int insertPos) {
        int totalKeys = branchingFactor + 1;
        long[] tempKeys = new long[totalKeys];
        Node[] tempChildren = new Node[totalKeys + 1];

        for (int i = 0; i <= branchingFactor; i++) {
            tempChildren[i] = node.children[i];
        }

        for (int i = branchingFactor; i > insertPos; i--) {
            tempKeys[i] = tempKeys[i - 1] != 0 ? tempKeys[i - 1] : node.keys[i - 1];
        }

        for (int i = 0; i < branchingFactor; i++) {
            tempKeys[i] = node.keys[i];
        }

        for (int i = branchingFactor; i > insertPos; i--) {
            tempKeys[i] = tempKeys[i - 1];
            tempChildren[i + 1] = tempChildren[i];
        }

        tempKeys[insertPos] = key;
        tempChildren[insertPos + 1] = newChild;

        int splitPoint = totalKeys / 2;
        long promotedKey = tempKeys[splitPoint];

        node.keyCount = 0;
        for (int i = 0; i < splitPoint; i++) {
            node.keys[i] = tempKeys[i];
            node.children[i] = tempChildren[i];
            node.keyCount++;
        }
        node.children[splitPoint] = tempChildren[splitPoint];
        node.rebuildSketch();

        Node newNode = new Node(false, branchingFactor);
        newNode.keyCount = 0;
        for (int i = splitPoint + 1; i < totalKeys; i++) {
            newNode.keys[newNode.keyCount] = tempKeys[i];
            newNode.children[newNode.keyCount] = tempChildren[i];
            newNode.keyCount++;
        }
        newNode.children[newNode.keyCount] = tempChildren[totalKeys];
        newNode.rebuildSketch();

        totalNodes += 2;
        return new SplitResult(promotedKey, newNode);
    }

    private int findChildIndex(Node node, long key) {
        return node.findChildIndexFusion(key);
    }

    private int findInsertPos(Node node, long key) {
        return node.findInsertPosFusion(key);
    }

    private void shiftRight(Node leaf, int pos) {
        for (int i = leaf.keyCount; i > pos; i--) {
            leaf.keys[i] = leaf.keys[i - 1];
            leaf.records[i] = leaf.records[i - 1];
        }
    }

    private void shiftRightInternal(Node node, int pos) {
        for (int i = node.keyCount; i > pos; i--) {
            node.keys[i] = node.keys[i - 1];
            node.children[i + 1] = node.children[i];
        }
        node.children[pos + 1] = node.children[pos];
    }

    public List<LogRecord> searchExact(long epochMillis) {
        if (root == null) return Collections.emptyList();
        Node node = root;

        while (!node.isLeaf) {
            int idx = findChildIndex(node, epochMillis);
            node = node.children[Math.min(idx, node.keyCount)];
        }

        int pos = binarySearchLeaf(node, epochMillis);
        if (pos >= 0) {
            return new ArrayList<>((List<LogRecord>) node.records[pos]);
        }
        return Collections.emptyList();
    }

    private int binarySearchLeaf(Node leaf, long key) {
        return leaf.findLeafKeyIndexFusion(key);
    }

    public List<LogRecord> searchRange(long startEpoch, long endEpoch) {
        List<LogRecord> results = new ArrayList<>();
        if (root == null) return results;
        rangeSearch(root, startEpoch, endEpoch, results);
        return results;
    }

    private void rangeSearch(Node node, long start, long end, List<LogRecord> results) {
        if (node.isLeaf) {
            for (int i = 0; i < node.keyCount; i++) {
                if (node.keys[i] > end) break;
                if (node.keys[i] >= start) {
                    results.addAll((List<LogRecord>) node.records[i]);
                }
            }
            return;
        }

        int startChild = findChildIndex(node, start);
        int endChild = findChildIndex(node, end);
        for (int i = startChild; i <= Math.min(endChild, node.keyCount); i++) {
            rangeSearch(node.children[i], start, end, results);
        }
    }

    public boolean deleteRecord(LogRecord record) {
        if (record == null || root == null) return false;
        boolean[] deleted = {false};
        deleteRecordRec(root, record.getEpochMillis(), record.getLogId(), deleted);
        if (deleted[0] && !root.isLeaf && root.keyCount == 0) {
            root = root.children[0];
        }
        return deleted[0];
    }

    private void deleteRecordRec(Node node, long key, long logId, boolean[] deleted) {
        if (node.isLeaf) {
            int pos = binarySearchLeaf(node, key);
            if (pos >= 0) {
                List<LogRecord> list = (List<LogRecord>) node.records[pos];
                boolean removed = list.removeIf(r -> r.getLogId() == logId);
                if (removed) {
                    deleted[0] = true;
                    if (list.isEmpty()) {
                        for (int i = pos; i < node.keyCount - 1; i++) {
                            node.keys[i] = node.keys[i + 1];
                            node.records[i] = node.records[i + 1];
                        }
                        node.keyCount--;
                        node.rebuildSketch();
                    }
                }
            }
            return;
        }

        int idx = findChildIndex(node, key);
        deleteRecordRec(node.children[Math.min(idx, node.keyCount)], key, logId, deleted);
    }

    public boolean delete(long epochMillis) {
        if (root == null) return false;
        boolean[] deleted = {false};
        deleteRec(root, epochMillis, deleted);
        if (deleted[0]) {
            if (!root.isLeaf && root.keyCount == 0) {
                root = root.children[0];
            }
        }
        return deleted[0];
    }

    private void deleteRec(Node node, long key, boolean[] deleted) {
        if (node.isLeaf) {
            int pos = binarySearchLeaf(node, key);
            if (pos >= 0) {
                for (int i = pos; i < node.keyCount - 1; i++) {
                    node.keys[i] = node.keys[i + 1];
                    node.records[i] = node.records[i + 1];
                }
                node.keyCount--;
                node.rebuildSketch();
                deleted[0] = true;
            }
            return;
        }

        int idx = findChildIndex(node, key);
        deleteRec(node.children[Math.min(idx, node.keyCount)], key, deleted);
    }

    public int size() {
        return totalNodes;
    }

    public void clear() {
        root = null;
        totalNodes = 0;
    }
}
