# Log Analytics & Search Engine (DSA Capstone)

> A high-performance, distributed-ready in-memory Log Search and Observability Analytics Engine built from scratch in pure Java (zero external dependencies).

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [The Core Problem](#2-the-core-problem)
3. [Architecture & System Design](#3-architecture--system-design)
4. [How the System Works](#4-how-the-system-works)
   - [Phase 1: Ingestion & CSV Parsing](#phase-1-ingestion--csv-parsing)
   - [Phase 2: Dual Index Construction](#phase-2-dual-index-construction)
   - [Phase 3: Query Execution (Single-Node vs Distributed)](#phase-3-query-execution)
   - [Phase 4: Intelligence & Analytics Workflows](#phase-4-intelligence--analytics-workflows)
5. [Data Structures & Algorithms Rationale](#5-data-structures--algorithms-rationale)
   - [Text Indexing: Radix Tree (Compressed Patricia Trie)](#51-text-indexing-radix-tree-compressed-patricia-trie)
   - [Temporal Indexing: Multi-Way Search Tree (Fusion Tree Approximation)](#52-temporal-indexing-multi-way-search-tree-fusion-tree-approximation)
   - [Baseline Structures: Standard Trie & AVL Tree](#53-baseline-structures-standard-trie--avl-tree)
   - [Distributed Stream Merging: Min-Heap K-Way Merge Sort](#54-distributed-stream-merging-min-heap-k-way-merge-sort)
   - [Heavy-Hitters: Bounded Min-Heap (Top-K)](#55-heavy-hitters-bounded-min-heap-top-k)
   - [Statistical Anomaly Detection: Sliding-Window Z-Score Bucketing](#56-statistical-anomaly-detection-sliding-window-z-score-bucketing)
   - [Log Template Mining: Parameter Normalization & Drain-Style Clustering](#57-log-template-mining-parameter-normalization--drain-style-clustering)
   - [Hash Partitioning & Asynchronous Scatter-Gather](#58-hash-partitioning--asynchronous-scatter-gather)
6. [Algorithmic Complexity Matrix](#6-algorithmic-complexity-matrix)
7. [Repository File Map](#7-repository-file-map)
8. [Setup, Build & Execution Guide](#8-setup-build--execution-guide)
9. [Automated Verification & Benchmarking](#9-automated-verification--benchmarking)

---

## 1. Project Overview

Modern microservice architectures and cloud infrastructure generate tens of millions of semi-structured log events daily. Diagnosing outages, latency spikes, and security breaches requires searching across text bodies, time intervals, and metadata attributes simultaneously under strict latency constraints.

This project is a clean-room, educational implementation of core mechanisms powering industrial observability platforms such as **Elasticsearch / Apache Lucene** (inverted text indices), **ClickHouse / InfluxDB** (high-throughput time-series range engines), and **Drain / Logram** (unsupervised log template extraction).

The engine is written in **pure Java (JDK 8+) with zero external dependencies**, relying exclusively on custom data structures and Java SE standard libraries (`java.util`, `java.time`, `java.util.concurrent`, `java.awt`).

---

## 2. The Core Problem

Traditional database queries (`SELECT * WHERE message LIKE '%timeout%'`) and command-line utilities (`grep`) perform full table/file scans operating in $O(N)$ time. When dealing with gigabytes of logs:
1. **Linear Search Bottlenecks**: Scanning 1,000,000 log records sequentially for a keyword or timestamp range takes hundreds of milliseconds to seconds per query.
2. **Memory Explosion**: Standard character tries consume massive amounts of heap memory due to pointer bloat and 16-byte object headers for every single character.
3. **Temporal Skew**: Logs arrive continuously in near-sorted order; unbalanced search trees degenerate into linked lists with $O(N)$ depth.
4. **Unstructured Data Chaos**: Error messages contain dynamic variable payloads (IPs, UUIDs, timestamps, hex codes), making clustering and incident detection difficult without automated parameter masking.

This project solves these challenges by combining **multi-dimensional custom indexing**, **in-process distributed sharding**, and **statistical/heuristic data mining**.

---

## 3. Architecture & System Design

```
                                  +-----------------------+
                                  |     UI.java / CLI     |
                                  +-----------+-----------+
                                              |
                     +------------------------+------------------------+
                     |                                                 |
                     v                                                 v
         [Single-Node Engine]                               [Distributed Cluster]
+------------------------------------+             +------------------------------------+
|  QueryEngine.java                  |             | ClusterCoordinator.java           |
|  - CompressedTrie (Radix Tree)     |             | - Hash Partitioning (logId)        |
|  - FusionTree (Multi-Way B-Tree)   |             | - CompletableFuture Scatter-Gather |
|  - Set Intersections & Filters     |             | - PriorityQueue K-Way Merge Sort   |
+------------------------------------+             +-----------------+------------------+
                                                                     |
                                             +-----------------------+-----------------------+
                                             |                       |                       |
                                             v                       v                       v
                                      +--------------+        +--------------+        +--------------+
                                      | ShardWorker0 |        | ShardWorker1 |        | ShardWorker2 |
                                      +--------------+        +--------------+        +--------------+
```

---

## 4. How the System Works

### Phase 1: Ingestion & CSV Parsing
1. **Dataset Generation / Loading**: Datasets ($10\text{k}$ to $1\text{M}$ records) are loaded from CSV or dynamically generated by `DatasetGenerator.java`.
2. **Record Modeling**: Each row is parsed via `LogRecord.fromCsvLine()`, applying regex-safe CSV splitting that preserves commas enclosed in quotation marks:
   - `logId`: 64-bit unique integer identifier.
   - `timestamp` / `epochMillis`: Human-readable date-time string mapped to an atomic 64-bit primitive millisecond epoch timestamp.
   - `level`: Severity classification (`INFO`, `WARN`, `ERROR`, `DEBUG`).
   - `service`: Microservice identity (`API`, `Database`, `Authentication`, `Payment`, etc.).
   - `message`: Unstructured text payload.

### Phase 2: Dual Index Construction
When a dataset is loaded into the engine (`UI.java`), each record is indexed simultaneously across two tiers:
1. **Single-Node Tier**:
   - `CompressedTrie.insertRecord(record)`: Tokenizes message strings, service names, and levels, inserting each token into the Radix Tree with edge compression.
   - `FusionTree.insert(record)`: Inserts `epochMillis` into the multi-way tree, splitting nodes and promoting median keys as capacity thresholds ($B \le 62$) are reached.
2. **Distributed Cluster Tier**:
   - `ClusterCoordinator.ingest(record)`: Hashes `logId` deterministically and assigns the record to one of $K$ worker shards (`ShardWorker`). Each shard maintains its own independent, thread-isolated Radix Tree and Fusion Tree.

### Phase 3: Query Execution

#### A. Single-Node Combined Query (`QueryEngine.java`)
1. **Text Search**: If a keyword, prefix, or wildcard is provided, the Radix Tree searches for matching candidates in $O(L)$ or branch-pruned wildcard time.
2. **Timestamp Range Pruning**: If a time window $[T_{\text{start}}, T_{\text{end}}]$ is specified, `FusionTree.searchRange()` prunes out non-overlapping subtrees and returns time-matched candidates.
3. **Set Intersection**: Candidate sets from text and temporal queries are intersected using `HashSet.retainAll()`, eliminating non-overlapping results in $O(\min(|A|, |B|))$ average time.
4. **Metadata Filtering**: Remaining candidates are checked against log level and service predicates.
5. **Ordering**: The final candidates are returned sorted chronologically by timestamp.

#### B. Distributed Scatter-Gather Query (`ClusterCoordinator.java`)
1. **Scatter**: The coordinator fans out the query across all $K$ worker shards concurrently using an `ExecutorService` thread pool and `CompletableFuture.supplyAsync`.
2. **Parallel Shard Execution**: Each `ShardWorker` evaluates the query locally on its private in-memory indices without lock contention.
3. **Gather & K-Way Merge**: The coordinator joins all futures and streams the individually sorted shard outputs through a **Min-Heap Priority Queue (`PriorityQueue<ShardCursor>`)**. The smallest timestamp cursor is continuously polled and advanced, producing a chronologically sorted result stream in $O(M \log K)$ time.

### Phase 4: Intelligence & Analytics Workflows

1. **Sliding-Window Z-Score Anomaly Detection (`AnomalyDetector.java`)**:
   - Discretizes the entire timeline into uniform 5-minute buckets (`WindowBucket[]`) via arithmetic indexing.
   - Accumulates error and warning counts per bucket.
   - Computes overall mean ($\mu$) and standard deviation ($\sigma$).
   - Flags operational spikes where $Z = \frac{x - \mu}{\sigma} \ge 2.5$ and pinpoints the primary culprit microservice within each spike window.
2. **Log Template Mining (`LogTemplateMiner.java`)**:
   - Normalizes variable entities (UUIDs, IP addresses, memory addresses, integers, hex codes) into a unified `<*>` placeholder.
   - Groups structural templates into a hash cluster map, quantifying event distributions across services and log levels.
3. **Top-K Error Analytics (`TopKAnalytics.java`)**:
   - Streams distinct error categories into a bounded Min-Heap of size $K$, extracting heavy-hitter failure modes in $O(N \log K)$ time without a full dataset sort.

---

## 5. Data Structures & Algorithms Rationale

### 5.1. Text Indexing: Radix Tree (Compressed Patricia Trie)
* **File**: `CompressedTrie.java`
* **What it is**: A space-optimized trie variant where every internal node with only one child is merged with its child edge, storing a string `edgeLabel` rather than a single character.
* **Why it was used**:
  * **$O(L)$ Search Complexity**: Search time depends strictly on the query token length $L$, independent of the dataset size $N$. Searching 1,000,000 logs takes the same fraction of a microsecond as searching 1,000 logs.
  * **Memory Optimization**: Standard character-level tries suffer severe object-header overhead in Java (16 bytes per node + reference pointers). By compressing continuous chains into single strings, the Radix Tree cuts node count and heap usage by up to **$70\%$**.
  * **Branch-Pruned Wildcards**: Supports `*` and `?` patterns by traversing compressed edge segments, pruning non-matching subtrees early.
  * **Safe Dynamic Deletion**: When records are deleted, empty leaves are unlinked and single-child parents are merged upward to maintain structural compression.

### 5.2. Temporal Indexing: Multi-Way Search Tree (Fusion Tree Approximation)
* **File**: `FusionTree.java`
* **What it is**: A multi-way search tree with a high branching factor ($B \le 62$, matching 64-bit architecture word boundaries). Internal nodes store contiguous primitive arrays: `long[] keys`, `List[] records`, and `Node[] children`.
* **Why it was used**:
  * **Minimized Tree Depth**: Standard binary search trees have a branching factor of 2, requiring $\approx \log_2(1,000,000) \approx 20$ pointer hops. With $B=62$, tree depth collapses to $\log_{62}(1,000,000) \approx 3\text{ to }4$ levels.
  * **Hardware Cache Locality**: Traversing binary trees dereferences individual heap pointers repeatedly, triggering CPU cache misses. In this multi-way tree, keys are laid out sequentially in primitive arrays, allowing fast binary search (`(lo + hi) >>> 1`) directly in CPU L1/L2 cache.
  * **Sub-linear Range Pruning**: For interval searches $[T_{\text{start}}, T_{\text{end}}]$, entire subtrees whose key bounds fall outside the range are skipped without inspection.

### 5.3. Baseline Structures: Standard Trie & AVL Tree
* **Files**: `StandardTrie.java`, `BinarySearchTree.java`
* **What they are**: A character-by-character trie and a self-balancing AVL Tree using height balancing and tree rotations (Left, Right, Left-Right, Right-Left).
* **Why they were used**:
  * Built as **empirical baselines** for the benchmarking suite (`Benchmark.java`).
  * Demonstrates why theoretical $O(\log_2 N)$ binary search structures and uncompressed tries fall short in real-world systems due to pointer overhead and cache inefficiency compared to Radix Trees and Multi-Way Trees.

### 5.4. Distributed Stream Merging: Min-Heap K-Way Merge Sort
* **File**: `ClusterCoordinator.java`
* **What it is**: A priority queue (`PriorityQueue<ShardCursor>`) holding active iterators from $K$ pre-sorted shard streams, ordered by timestamp.
* **Why it was used**:
  * **Complexity Optimization ($O(M \log K)$ vs $O(M \log M)$)**: If $K$ shards return $M$ total matching records, pooling them into a single list and sorting requires $O(M \log M)$ time. By utilizing a min-heap of size $K$, extracting the next earliest record takes only $O(\log K)$ heap adjustments. Since $K \ll M$ (e.g., $K=3$ shards, $M=50,000$ results), the merge executes in near-linear time and streams results incrementally.

### 5.5. Heavy-Hitters: Bounded Min-Heap (Top-K)
* **File**: `TopKAnalytics.java`
* **What it is**: A Min-Heap (`PriorityQueue<FrequencyEntry>`) strictly bounded to capacity $K$.
* **Why it was used**:
  * **Space & Time Efficiency**: Finding the top 5 most frequent error types from 50,000 unique error messages does not require sorting all 50,000 entries ($O(D \log D)$).
  * Maintains only the top $K$ items: if an incoming error count exceeds the heap root (`minHeap.peek()`), the minimum is ejected and replaced in $O(\log K)$ time, completing the analytics pass in $O(N \log K)$ time with $O(K)$ auxiliary heap memory.

### 5.6. Statistical Anomaly Detection: Sliding-Window Z-Score Bucketing
* **File**: `AnomalyDetector.java`
* **What it is**: An array of contiguous time buckets (`WindowBucket[]`) representing fixed 5-minute intervals.
* **Why it was used**:
  * **$O(1)$ Arithmetic Indexing**: Instead of performing costly window joins, any log's bucket index is calculated directly:
    $$\text{bucketIndex} = \left\lfloor \frac{\text{epochMillis} - \text{minEpoch}}{\text{windowSizeMillis}} \right\rfloor$$
  * **Vectorized Statistical Computation**: Enables a single linear pass to compute the population mean ($\mu$) and standard deviation ($\sigma$). Flags statistical outliers exceeding $Z \ge 2.5$ without requiring complex machine learning runtimes.

### 5.7. Log Template Mining: Parameter Normalization & Drain-Style Clustering
* **File**: `LogTemplateMiner.java`
* **What it is**: A regular-expression-based token normalizer coupled with an in-memory cluster map (`Map<String, TemplateCluster>`).
* **Why it was used**:
  * Raw logs differ by IDs, IPs, and codes (e.g., `Failed auth for user 102` vs `Failed auth for user 999`).
  * By abstracting dynamic tokens into `<*>` placeholders, the algorithm reduces millions of lines into a few dozen core event signatures in $O(N \cdot W)$ time (where $W$ is message token count), enabling unsupervised grouping.

### 5.8. Hash Partitioning & Asynchronous Scatter-Gather
* **Files**: `ClusterCoordinator.java`, `ShardWorker.java`
* **What it is**: Modulo hash routing (`Math.floorMod(Long.hashCode(logId), numShards)`) and non-blocking multi-threaded dispatch via `CompletableFuture`.
* **Why it was used**:
  * **Uniform Load Balancing**: Distributes logs uniformly across partitions, avoiding hot spots.
  * **Thread Isolation**: Eliminates write-lock contention by ensuring each shard thread writes to and queries its own distinct memory structures.

---

## 6. Algorithmic Complexity Matrix

| Component / Task | Data Structure / Algorithm | Time Complexity (Exact / Insert) | Time Complexity (Range / Search) | Space Complexity |
| :--- | :--- | :--- | :--- | :--- |
| **Linear Text Search** | Baseline Naive Scan | $O(N \cdot L)$ | $O(N \cdot L)$ | $O(1)$ auxiliary |
| **Standard Trie Search** | Standard Character Trie | $O(L)$ search / $O(L)$ insert | $O(L + M)$ prefix | $O(\sum L \cdot |\Sigma|)$ (high node overhead) |
| **Optimized Text Search**| **Radix Tree (`CompressedTrie`)** | **$O(L)$ search / $O(L)$ insert** | **$O(L + M)$ prefix; branch-pruned glob** | **$O(\text{distinct edges})$ (up to 70% less memory)** |
| **Linear Time Search** | Baseline Naive Scan | $O(N)$ | $O(N)$ | $O(1)$ auxiliary |
| **AVL Tree Search** | Self-Balancing BST | $O(\log_2 N)$ | $O(\log_2 N + M)$ range | $O(N)$ (2 pointers + height per node) |
| **Optimized Time Search**| **Multi-Way Fusion Tree (`FusionTree`)** | **$O(\log_B N)$ ($B \le 8$)** | **$O(\log_B N + M)$ range** | **$O(N)$ (Word-RAM bit sketches, low height)** |
| **Distributed Merging** | **$K$-Way Min-Heap** | $O(\log K)$ per poll/insert | **$O(M \log K)$ total merge** | **$O(K)$ heap memory** |
| **Top-K Analytics** | **Bounded Min-Heap** | $O(N \log K)$ streaming | $O(K \log K)$ final order | **$O(D + K)$** ($D$ = distinct errors) |
| **Anomaly Detection** | **Sliding Window Array** | $O(N)$ bucket assignment | $O(W)$ statistical pass | **$O(W)$** ($W$ = number of time windows) |
| **Template Discovery** | **Drain Token Abstraction** | $O(N \cdot W)$ | $O(C \log C)$ cluster sort | **$O(C)$** ($C$ = unique template count) |

*(Where $N$ = record count, $L$ = token length, $M$ = matched record count, $B$ = branching factor up to 8, $K$ = number of shards or top errors, $W$ = tokens per message, $D$ = distinct error categories, $C$ = distinct templates).*

---

## 7. Repository File Map

```
.
├── README.md                 # Project documentation and architectural manual
├── LogRecord.java            # Immutable log model with CSV parsing & formatting
├── CompressedTrie.java       # Radix Tree with edge compression, globs, and deletion
├── StandardTrie.java         # Uncompressed character trie for baseline comparison
├── LinearTextSearch.java     # Brute-force linear scan text search baseline
├── FusionTree.java           # Fredman-Willard Word-RAM 64-bit timestamp index
├── BinarySearchTree.java     # Self-balancing AVL tree for temporal baseline
├── LinearTimeSearch.java     # Brute-force linear scan timestamp baseline
├── QueryEngine.java          # Unified multi-dimensional query planner (sets & filters)
├── ShardWorker.java          # In-memory worker partition with private indices
├── ClusterCoordinator.java   # Hash router, scatter-gather executor, and K-way merge
├── AnomalyDetector.java      # Sliding-window Z-Score statistical anomaly detector
├── LogTemplateMiner.java     # Drain-style unsupervised log template mining
├── TopKAnalytics.java        # Bounded min-heap heavy-hitter error analyzer
├── DatasetGenerator.java     # Synthetic CSV log generator (10K to 1M rows)
├── Benchmark.java            # Performance benchmarking harness (writes to CSV)
├── GraphGenerator.java       # Zero-dependency Java AWT chart rendering engine
├── TestVerification.java     # Automated verification suite (parity, ordering, deletion)
├── UI.java                   # Interactive 15-option terminal user interface
└── data/
    └── logs_100k.csv         # Default generated 100,000 log record dataset
```

---

## 8. Setup, Build & Execution Guide

### Prerequisites
* **Java Development Kit (JDK 8 or higher)** installed and available on your system `PATH`.
* No external libraries, build tools (Maven/Gradle), or external databases are required.

### Compilation
From the root project directory, compile all Java source files:

```bash
javac *.java
```

### Running the Interactive Console (UI)
Launch the primary command-line interface:

```bash
java UI
```

The interactive menu will guide you through 15 operations:
```
========================================
       LOG ANALYTICS SEARCH ENGINE      
========================================
Active Dataset: logs_100k.csv | Records: 100,000 | Shards: 3
----------------------------------------
--- Single-Node Core Search ---
1.  Keyword Search
2.  Prefix Search
3.  Wildcard Search
4.  Time Range Search
5.  Filter by Log Level
6.  Combined Search
7.  Top-K Errors
8.  Insert New Log
9.  Delete Log
10. Performance Benchmark
--- Distributed & Intelligence Extensions ---
11. Distributed Scatter-Gather Search (K-Way Merge)
12. View Cluster Topology & Shard Distribution
13. Algorithmic Anomaly Detection (Z-Score Spikes)
14. Log Template Discovery (Clustering)
15. Exit
```

---

## 9. Automated Verification & Benchmarking

### 1. Running the Automated Verification Suite
Verify system integrity, shard load distribution, single vs. distributed result parity, heap sorting order, and safe dynamic deletion:

```bash
java TestVerification
```

Expected output:
* Shard distribution verification across partitions.
* Result parity check (single-node vs. distributed scatter-gather matching 100%).
* Strict chronological ordering check on $K$-way min-heap merge.
* Confirmation of safe dynamic deletion on sibling records.

### 2. Running Benchmarks
Execute the performance benchmarking harness across 10K, 50K, 100K, 500K, and 1,000,000 log datasets:

```bash
java Benchmark
```
Results will be output to the console and exported to `results/benchmark_results.csv`.

### 3. Generating Performance Graphs
Render high-resolution visual benchmark graphs from the generated CSV:

```bash
java GraphGenerator
```
This produces anti-aliased PNG charts inside the `results/` folder:
* `search_time_graph.png`: Linear Search vs. Standard Trie vs. Compressed Trie (Radix).
* `timestamp_search_graph.png`: Linear Scan vs. AVL Tree vs. Fusion Tree.
* `insertion_time_graph.png`: Insertion latency across data structures.
