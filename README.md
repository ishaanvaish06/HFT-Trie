# Intelligent Distributed Log Analytics and Search Engine
### Using Hybrid Fusion Tree and Compressed Trie

A high-performance, in-memory log search and observability engine built in pure Java (zero external dependencies).

## Source Files
1. **LogRecord.java**: Structured log data model with 64-bit epoch timestamps and CSV parsing.
2. **CompressedTrie.java**: Radix tree with edge compression, prefix matching, and wildcard matching (`*`).
3. **FusionTree.java**: Multi-way tree ($B=8$) with Fredman-Willard bitwise distinguishing-bit extraction (XOR) and range query subtree pruning.
4. **LogAnalyticsEngine.java**: Complete orchestration containing:
   - Compound Query Planner (intersects text and time ranges)
   - 3-Node Hash Sharding with parallel scatter-gather (`CompletableFuture`)
   - Min-Heap $K$-Way Stream Merge Sort (`PriorityQueue`)
   - Sliding-Window Z-Score Anomaly Detector ($Z \ge 2.0$)
   - Drain-Style Log Template Discovery with `<*>` masking
   - Bounded Min-Heap Top-$K$ Heavy-Hitter Errors
   - Interactive 12-Option CLI & Algorithmic Benchmark Runner

## How to Compile & Run

```bash
# Compile all source files
javac *.java

# Launch the interactive engine
java LogAnalyticsEngine
```