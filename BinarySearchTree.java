import java.util.ArrayList;
import java.util.List;

public class BinarySearchTree {

    private static class Node {
        long key;
        List<LogRecord> records = new ArrayList<>(4);
        Node left, right;
        int height;

        Node(long key, LogRecord record) {
            this.key = key;
            this.records.add(record);
            this.height = 1;
        }
    }

    private Node root;

    private int height(Node n) {
        return n == null ? 0 : n.height;
    }

    private int getBalance(Node n) {
        return n == null ? 0 : height(n.left) - height(n.right);
    }

    private Node rightRotate(Node y) {
        Node x = y.left;
        Node T2 = x.right;
        x.right = y;
        y.left = T2;
        y.height = Math.max(height(y.left), height(y.right)) + 1;
        x.height = Math.max(height(x.left), height(x.right)) + 1;
        return x;
    }

    private Node leftRotate(Node x) {
        Node y = x.right;
        Node T2 = y.left;
        y.left = x;
        x.right = T2;
        x.height = Math.max(height(x.left), height(x.right)) + 1;
        y.height = Math.max(height(y.left), height(y.right)) + 1;
        return y;
    }

    public void insert(LogRecord record) {
        root = insertRec(root, record.getEpochMillis(), record);
    }

    private Node insertRec(Node node, long key, LogRecord record) {
        if (node == null) return new Node(key, record);

        if (key < node.key) {
            node.left = insertRec(node.left, key, record);
        } else if (key > node.key) {
            node.right = insertRec(node.right, key, record);
        } else {
            node.records.add(record);
            return node;
        }

        node.height = 1 + Math.max(height(node.left), height(node.right));
        int balance = getBalance(node);

        if (balance > 1 && key < node.left.key)
            return rightRotate(node);

        if (balance < -1 && key > node.right.key)
            return leftRotate(node);

        if (balance > 1 && key > node.left.key) {
            node.left = leftRotate(node.left);
            return rightRotate(node);
        }

        if (balance < -1 && key < node.right.key) {
            node.right = rightRotate(node.right);
            return leftRotate(node);
        }

        return node;
    }

    public List<LogRecord> searchExact(long epochMillis) {
        Node curr = root;
        while (curr != null) {
            if (epochMillis < curr.key) {
                curr = curr.left;
            } else if (epochMillis > curr.key) {
                curr = curr.right;
            } else {
                return new ArrayList<>(curr.records);
            }
        }
        return new ArrayList<>();
    }

    public List<LogRecord> searchRange(long startEpoch, long endEpoch) {
        List<LogRecord> results = new ArrayList<>();
        rangeRec(root, startEpoch, endEpoch, results);
        return results;
    }

    private void rangeRec(Node node, long start, long end, List<LogRecord> results) {
        if (node == null) return;
        if (start < node.key) {
            rangeRec(node.left, start, end, results);
        }
        if (start <= node.key && node.key <= end) {
            results.addAll(node.records);
        }
        if (end > node.key) {
            rangeRec(node.right, start, end, results);
        }
    }
}
