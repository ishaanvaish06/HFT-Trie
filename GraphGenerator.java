import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class GraphGenerator {

    private static final String CSV_PATH = "results" + File.separator + "benchmark_results.csv";
    private static final String RESULTS_DIR = "results";

    public static void main(String[] args) {
        System.out.println("Generating benchmark graphs...");

        File csvFile = new File(CSV_PATH);
        if (!csvFile.exists()) {
            System.err.println("Error: " + CSV_PATH + " not found.");
            return;
        }

        List<Integer> datasetSizes = new ArrayList<>();
        List<Double> linearKwNs = new ArrayList<>();
        List<Double> stdTrieKwNs = new ArrayList<>();
        List<Double> compTrieKwNs = new ArrayList<>();
        List<Double> linearRangeNs = new ArrayList<>();
        List<Double> bstRangeNs = new ArrayList<>();
        List<Double> fusionTreeRangeNs = new ArrayList<>();
        List<Double> stdTrieInsertMs = new ArrayList<>();
        List<Double> compTrieInsertMs = new ArrayList<>();
        List<Double> bstInsertMs = new ArrayList<>();
        List<Double> fusionTreeInsertMs = new ArrayList<>();
        List<Double> totalIndexingMs = new ArrayList<>();
        List<Double> stdTrieMemMb = new ArrayList<>();
        List<Double> compTrieMemMb = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFile))) {
            String headerLine = br.readLine();
            String[] headers = headerLine.split(",");
            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colMap.put(headers[i].trim(), i);
            }

            String line;
            while ((line = br.readLine()) != null) {
                String[] cols = line.split(",");
                if (cols.length < headers.length) continue;

                datasetSizes.add(Integer.parseInt(cols[colMap.get("DatasetSize")].trim()));
                linearKwNs.add(Double.parseDouble(cols[colMap.get("LinearTextSearch_Keyword_ns")].trim()));
                stdTrieKwNs.add(Double.parseDouble(cols[colMap.get("StandardTrie_Keyword_ns")].trim()));
                compTrieKwNs.add(Double.parseDouble(cols[colMap.get("CompressedTrie_Keyword_ns")].trim()));
                linearRangeNs.add(Double.parseDouble(cols[colMap.get("LinearTimeSearch_Range_ns")].trim()));
                bstRangeNs.add(Double.parseDouble(cols[colMap.get("BST_Range_ns")].trim()));
                fusionTreeRangeNs.add(Double.parseDouble(cols[colMap.get("FusionTree_Range_ns")].trim()));
                stdTrieInsertMs.add(Double.parseDouble(cols[colMap.get("StandardTrie_Insert_ms")].trim()));
                compTrieInsertMs.add(Double.parseDouble(cols[colMap.get("CompressedTrie_Insert_ms")].trim()));
                bstInsertMs.add(Double.parseDouble(cols[colMap.get("BST_Insert_ms")].trim()));
                fusionTreeInsertMs.add(Double.parseDouble(cols[colMap.get("FusionTree_Insert_ms")].trim()));
                totalIndexingMs.add(Double.parseDouble(cols[colMap.get("TotalIndexingTime_ms")].trim()));
                stdTrieMemMb.add(Double.parseDouble(cols[colMap.get("StandardTrie_Mem_MB")].trim()));
                compTrieMemMb.add(Double.parseDouble(cols[colMap.get("CompressedTrie_Mem_MB")].trim()));
            }
        } catch (IOException e) {
            System.err.println("Failed to read benchmark CSV: " + e.getMessage());
            return;
        }

        List<String> labels = new ArrayList<>();
        for (int s : datasetSizes) {
            labels.add(s >= 1000000 ? "1M" : (s / 1000) + "K");
        }

        drawLineChart(
            "Dataset Size vs Keyword Search Time",
            "Dataset Size", "Execution Time (ms)", labels,
            new String[]{"Linear Search", "Standard Trie", "Compressed Trie (Radix)"},
            new Color[]{new Color(0xE74C3C), new Color(0xF39C12), new Color(0x2ECC71)},
            new double[][]{
                toMs(linearKwNs), toMs(stdTrieKwNs), toMs(compTrieKwNs)
            },
            new String[]{"o", "s", "^"},
            RESULTS_DIR + File.separator + "search_time_graph.png"
        );

        drawLineChart(
            "Dataset Size vs Timestamp Search Time",
            "Dataset Size", "Execution Time (ms)", labels,
            new String[]{"Linear Search", "Binary Search Tree (BST)", "Fusion Tree"},
            new Color[]{new Color(0xE74C3C), new Color(0x3498DB), new Color(0x9B59B6)},
            new double[][]{
                toMs(linearRangeNs), toMs(bstRangeNs), toMs(fusionTreeRangeNs)
            },
            new String[]{"o", "s", "^"},
            RESULTS_DIR + File.separator + "timestamp_search_graph.png"
        );

        drawLineChart(
            "Dataset Size vs Insertion Time",
            "Dataset Size", "Insertion Time (ms)", labels,
            new String[]{"Standard Trie", "Compressed Trie", "BST", "Fusion Tree"},
            new Color[]{new Color(0xF39C12), new Color(0x2ECC71), new Color(0x3498DB), new Color(0x9B59B6)},
            new double[][]{
                toArray(stdTrieInsertMs), toArray(compTrieInsertMs),
                toArray(bstInsertMs), toArray(fusionTreeInsertMs)
            },
            new String[]{"o", "s", "^", "d"},
            RESULTS_DIR + File.separator + "insertion_time_graph.png"
        );

        drawBarChart(
            "Dataset Size vs Total Indexing Time",
            "Dataset Size", "Total Indexing Time (ms)", labels,
            toArray(totalIndexingMs),
            new Color(0x34495E),
            RESULTS_DIR + File.separator + "indexing_time_graph.png"
        );

        drawDashboard(
            "Log Engine Comparative Performance Overview", labels,
            toMs(linearKwNs), toMs(compTrieKwNs),
            toMs(linearRangeNs), toMs(fusionTreeRangeNs),
            toArray(stdTrieMemMb), toArray(compTrieMemMb),
            toArray(totalIndexingMs),
            RESULTS_DIR + File.separator + "comparison_graph.png"
        );

        System.out.println("All benchmark graphs generated successfully in /results directory:");
        System.out.println(" - results/search_time_graph.png");
        System.out.println(" - results/timestamp_search_graph.png");
        System.out.println(" - results/insertion_time_graph.png");
        System.out.println(" - results/indexing_time_graph.png");
        System.out.println(" - results/comparison_graph.png");
    }

    private static double[] toMs(List<Double> nsValues) {
        double[] ms = new double[nsValues.size()];
        for (int i = 0; i < nsValues.size(); i++) ms[i] = nsValues.get(i) / 1e6;
        return ms;
    }

    private static double[] toArray(List<Double> values) {
        double[] arr = new double[values.size()];
        for (int i = 0; i < values.size(); i++) arr[i] = values.get(i);
        return arr;
    }

    private static void drawLineChart(String title, String xLabel, String yLabel,
                                       List<String> labels, String[] seriesNames, Color[] colors,
                                       double[][] seriesData, String[] markers, String outputPath) {
        int w = 900, h = 550;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g, w, h);

        int padL = 80, padR = 40, padT = 70, padB = 70;
        int plotW = w - padL - padR;
        int plotH = h - padT - padB;

        double maxY = 0.001;
        for (double[] series : seriesData) {
            for (double v : series) if (v > maxY) maxY = v;
        }

        drawGridAndAxes(g, title, xLabel, yLabel, labels, maxY, padL, padT, plotW, plotH);

        for (int s = 0; s < seriesNames.length; s++) {
            g.setColor(colors[s]);
            g.setStroke(new BasicStroke(2.5f));
            double[] data = seriesData[s];

            int prevX = -1, prevY = -1;
            for (int i = 0; i < data.length; i++) {
                int cx = padL + (int) ((i / (double) (labels.size() - 1)) * plotW);
                int cy = padT + plotH - (int) ((data[i] / maxY) * plotH);

                if (prevX != -1) g.drawLine(prevX, prevY, cx, cy);
                drawMarker(g, cx, cy, markers[s], colors[s]);
                prevX = cx;
                prevY = cy;
            }
        }

        int legX = padL + 20, legY = padT + 20;
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        for (int s = 0; s < seriesNames.length; s++) {
            g.setColor(colors[s]);
            g.fillRect(legX, legY + s * 22, 16, 12);
            g.setColor(Color.DARK_GRAY);
            g.drawString(seriesNames[s], legX + 24, legY + s * 22 + 10);
        }

        g.dispose();
        saveImage(img, outputPath);
    }

    private static void drawBarChart(String title, String xLabel, String yLabel,
                                      List<String> labels, double[] data, Color barColor, String outputPath) {
        int w = 900, h = 550;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g, w, h);

        int padL = 80, padR = 40, padT = 70, padB = 70;
        int plotW = w - padL - padR;
        int plotH = h - padT - padB;

        double maxY = 0.001;
        for (double v : data) if (v > maxY) maxY = v;

        drawGridAndAxes(g, title, xLabel, yLabel, labels, maxY, padL, padT, plotW, plotH);

        int barW = plotW / (labels.size() * 2);
        for (int i = 0; i < data.length; i++) {
            int cx = padL + (int) (((i + 0.5) / (double) labels.size()) * plotW);
            int valH = (int) ((data[i] / maxY) * plotH);
            int bx = cx - barW / 2;
            int by = padT + plotH - valH;

            g.setColor(barColor);
            g.fillRect(bx, by, barW, valH);
            g.setColor(Color.BLACK);
            g.drawRect(bx, by, barW, valH);

            g.setFont(new Font("SansSerif", Font.BOLD, 11));
            String valStr = String.format("%,d ms", (long) data[i]);
            g.drawString(valStr, cx - g.getFontMetrics().stringWidth(valStr) / 2, by - 5);
        }

        g.dispose();
        saveImage(img, outputPath);
    }

    private static void drawDashboard(String title, List<String> labels,
                                       double[] kwLinMs, double[] kwCTMs,
                                       double[] tsLinMs, double[] tsFTMs,
                                       double[] memSTMb, double[] memCTMb,
                                       double[] totalIdxMs, String outputPath) {
        int w = 1400, h = 1000;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        setupGraphics(g, w, h);

        g.setFont(new Font("SansSerif", Font.BOLD, 20));
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, w / 2 - fm.stringWidth(title) / 2, 35);

        int pw = 620, ph = 380;
        int gapX = 40, gapY = 60;
        int startX = (w - 2 * pw - gapX) / 2;
        int startY = 60;

        drawPanelLine(g, startX, startY, pw, ph,
            "Keyword Search Time (ms)", labels,
            new String[]{"Linear Search", "Compressed Trie"},
            new Color[]{new Color(0xE74C3C), new Color(0x2ECC71)},
            new double[][]{kwLinMs, kwCTMs});

        drawPanelLine(g, startX + pw + gapX, startY, pw, ph,
            "Timestamp Search Time (ms)", labels,
            new String[]{"Linear Search", "Fusion Tree"},
            new Color[]{new Color(0xE74C3C), new Color(0x9B59B6)},
            new double[][]{tsLinMs, tsFTMs});

        drawPanelLine(g, startX, startY + ph + gapY, pw, ph,
            "Memory Usage (MB)", labels,
            new String[]{"Standard Trie", "Compressed Trie"},
            new Color[]{new Color(0xF39C12), new Color(0x2ECC71)},
            new double[][]{memSTMb, memCTMb});

        drawPanelBar(g, startX + pw + gapX, startY + ph + gapY, pw, ph,
            "Total Indexing Time (ms)", labels, totalIdxMs, new Color(0x16A085));

        g.dispose();
        saveImage(img, outputPath);
    }

    private static void drawPanelLine(Graphics2D g, int x, int y, int w, int h, String panelTitle,
                                      List<String> labels, String[] seriesNames, Color[] colors, double[][] seriesData) {
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.DARK_GRAY);
        g.drawString(panelTitle, x + 10, y + 20);

        int padL = 55, padR = 20, padT = 40, padB = 40;
        int pw = w - padL - padR, ph = h - padT - padB;

        double maxY = 0.001;
        for (double[] s : seriesData) for (double v : s) if (v > maxY) maxY = v;

        for (int i = 0; i <= 4; i++) {
            int gy = y + padT + ph - (int) ((i / 4.0) * ph);
            g.setColor(new Color(230, 230, 230));
            g.drawLine(x + padL, gy, x + padL + pw, gy);
            g.setColor(Color.GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(String.format("%.1f", (i / 4.0) * maxY), x + 5, gy + 4);
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        for (int i = 0; i < labels.size(); i++) {
            int gx = x + padL + (int) ((i / (double) (labels.size() - 1)) * pw);
            g.drawString(labels.get(i), gx - 10, y + padT + ph + 20);
        }

        for (int s = 0; s < seriesNames.length; s++) {
            g.setColor(colors[s]);
            g.setStroke(new BasicStroke(2.0f));
            double[] data = seriesData[s];
            int px = -1, py = -1;
            for (int i = 0; i < data.length; i++) {
                int cx = x + padL + (int) ((i / (double) (labels.size() - 1)) * pw);
                int cy = y + padT + ph - (int) ((data[i] / maxY) * ph);
                if (px != -1) g.drawLine(px, py, cx, cy);
                g.fillOval(cx - 3, cy - 3, 6, 6);
                px = cx; py = cy;
            }
        }

        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        int lx = x + padL + 10, ly = y + padT + 10;
        for (int s = 0; s < seriesNames.length; s++) {
            g.setColor(colors[s]);
            g.fillRect(lx, ly + s * 18, 12, 8);
            g.setColor(Color.DARK_GRAY);
            g.drawString(seriesNames[s], lx + 18, ly + s * 18 + 8);
        }
    }

    private static void drawPanelBar(Graphics2D g, int x, int y, int w, int h, String panelTitle,
                                     List<String> labels, double[] data, Color barColor) {
        g.setFont(new Font("SansSerif", Font.BOLD, 14));
        g.setColor(Color.DARK_GRAY);
        g.drawString(panelTitle, x + 10, y + 20);

        int padL = 55, padR = 20, padT = 40, padB = 40;
        int pw = w - padL - padR, ph = h - padT - padB;

        double maxY = 0.001;
        for (double v : data) if (v > maxY) maxY = v;

        for (int i = 0; i <= 4; i++) {
            int gy = y + padT + ph - (int) ((i / 4.0) * ph);
            g.setColor(new Color(230, 230, 230));
            g.drawLine(x + padL, gy, x + padL + pw, gy);
            g.setColor(Color.GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(String.format("%.0f", (i / 4.0) * maxY), x + 5, gy + 4);
        }

        int bw = pw / (labels.size() * 2);
        for (int i = 0; i < data.length; i++) {
            int cx = x + padL + (int) (((i + 0.5) / (double) labels.size()) * pw);
            int valH = (int) ((data[i] / maxY) * ph);
            int bx = cx - bw / 2;
            int by = y + padT + ph - valH;

            g.setColor(barColor);
            g.fillRect(bx, by, bw, valH);
            g.setColor(Color.BLACK);
            g.drawRect(bx, by, bw, valH);

            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            g.drawString(labels.get(i), cx - 10, y + padT + ph + 20);
        }
    }

    private static void setupGraphics(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
    }

    private static void drawGridAndAxes(Graphics2D g, String title, String xLabel, String yLabel,
                                         List<String> labels, double maxY,
                                         int padL, int padT, int plotW, int plotH) {

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.setColor(Color.BLACK);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(title, padL + plotW / 2 - fm.stringWidth(title) / 2, 35);

        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        fm = g.getFontMetrics();
        g.drawString(xLabel, padL + plotW / 2 - fm.stringWidth(xLabel) / 2, padT + plotH + 50);

        for (int i = 0; i <= 5; i++) {
            int gy = padT + plotH - (int) ((i / 5.0) * plotH);
            g.setColor(new Color(220, 220, 220));
            g.drawLine(padL, gy, padL + plotW, gy);

            g.setColor(Color.DARK_GRAY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            fm = g.getFontMetrics();
            String valStr = String.format("%.2f", (i / 5.0) * maxY);
            g.drawString(valStr, padL - fm.stringWidth(valStr) - 8, gy + 4);
        }

        for (int i = 0; i < labels.size(); i++) {
            int gx = padL + (int) ((i / (double) (labels.size() - 1)) * plotW);
            g.setColor(Color.DARK_GRAY);
            fm = g.getFontMetrics();
            g.drawString(labels.get(i), gx - fm.stringWidth(labels.get(i)) / 2, padT + plotH + 25);
        }

        g.setColor(Color.BLACK);
        g.drawRect(padL, padT, plotW, plotH);
    }

    private static void drawMarker(Graphics2D g, int cx, int cy, String type, Color color) {
        g.setColor(color);
        int r = 5;
        switch (type) {
            case "o": g.fillOval(cx - r, cy - r, 2 * r, 2 * r); break;
            case "s": g.fillRect(cx - r, cy - r, 2 * r, 2 * r); break;
            case "^":
                int[] xPoints = {cx, cx - r, cx + r};
                int[] yPoints = {cy - r - 1, cy + r, cy + r};
                g.fillPolygon(xPoints, yPoints, 3);
                break;
            case "d":
                int[] dx = {cx, cx + r, cx, cx - r};
                int[] dy = {cy - r, cy, cy + r, cy};
                g.fillPolygon(dx, dy, 4);
                break;
            default: g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        }
    }

    private static void saveImage(BufferedImage img, String path) {
        try {
            File out = new File(path);
            if (out.getParentFile() != null) out.getParentFile().mkdirs();
            ImageIO.write(img, "PNG", out);
        } catch (IOException e) {
            System.err.println("Failed to save " + path + ": " + e.getMessage());
        }
    }
}
