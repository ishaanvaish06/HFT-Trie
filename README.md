# HFT-Trie
A Hybrid Indexing Framework for Distributed Log Analytics framework

## Simple Project Workflow

```text
              ┌──────────────────┐
              │   Generate Logs  │
              │ / Read Log File  │
              └────────┬─────────┘
                       ↓
              ┌──────────────────┐
              │ Log Preprocessing│
              │ Parse each log   │
              └────────┬─────────┘
                       ↓
          ┌────────────┴────────────┐
          ↓                         ↓
 ┌──────────────────┐      ┌──────────────────┐
 │  Compressed Trie │      │    Fusion Tree   │
 │                  │      │                  │
 │ Keywords         │      │ Timestamp        │
 │ Error messages   │      │ Numeric values   │
 │ Prefix search    │      │ Range filtering  │
 └────────┬─────────┘      └────────┬─────────┘
          │                         │
          └────────────┬────────────┘
                       ↓
              ┌──────────────────┐
              │   Query Engine   │
              └────────┬─────────┘
                       ↓
       ┌───────────────┼───────────────┐
       ↓               ↓               ↓
   Keyword          Time/Level      Top-K Errors
    Search           Filter          Analytics
       │               │               │
       └───────────────┼───────────────┘
                       ↓
              ┌──────────────────┐
              │  Display Results │
              └──────────────────┘
```

This is enough for a **DSA capstone**.

---

# 1. Generate / Load Logs

Don't worry about actually collecting logs from IoT devices, cloud servers, etc.

Just create a dataset such as:

```text
2026-08-28 10:15:23 | ERROR | Database | Connection timeout
2026-08-28 10:15:25 | INFO  | Server   | Request received
2026-08-28 10:15:27 | ERROR | API      | Authentication failed
2026-08-28 10:15:29 | WARN  | Server   | High memory usage
```

You can generate **10K, 100K, 500K or 1M records** depending on your computer.

---

# 2. Log Preprocessing

Read each log and break it into fields:

```text
Timestamp
      ↓
Log Level
      ↓
Service
      ↓
Message
```

For example:

```text
2026-08-28 10:15:23 | ERROR | Database | Connection timeout
```

becomes:

```text
timestamp = 2026-08-28 10:15:23
level     = ERROR
service   = Database
message   = Connection timeout
```

---

# 3. Insert Into the Data Structures

This is the **main DSA part**.

### Compressed Trie

Insert important words from the log message.

Example:

```text
Connection timeout
Connection refused
Connection reset
```

The trie shares the common:

```text
Connection
```

So you can demonstrate why a **compressed trie** is useful.

It handles things like:

```text
con
conn
connection
```

for prefix-based searching.

Your presentation already identifies compressed tries as the structure for memory-efficient textual indexing and prefix/wildcard searching. 

---

# 4. Fusion Tree

Use the Fusion Tree for **numeric/ordered information**.

For example:

```text
Timestamp
```

can be converted into a numeric representation.

You can also maintain:

```text
Log ID
Timestamp
Error frequency
```

The Fusion Tree then allows you to demonstrate fast ordered searching/filtering.

Your current presentation specifically proposes using the Fusion Tree for timestamps and numerical log-level information. 

---

# 5. Query Engine

Now give the user a simple search interface.

For example:

```text
========= LOG SEARCH ENGINE =========

1. Search keyword
2. Prefix search
3. Search by time range
4. Filter by log level
5. Search by service
6. Top-K errors
7. Insert new log
8. Delete log
9. Exit
```

This is where your two data structures work together.

---

# 6. Example Query

User enters:

```text
Search: connection
```

### Compressed Trie

Finds:

```text
Connection timeout
Connection refused
Connection reset
```

Then the Fusion Tree can be used to apply:

```text
Timestamp:
10:00 → 12:00
```

Result:

```text
10:15:23 ERROR Database Connection timeout
10:32:51 ERROR API Connection refused
11:42:18 WARN  Server Connection reset
```

So you can demonstrate the **hybrid architecture**.

---

# 7. Top-K Error Analysis

This can be a relatively simple DSA component.

Count errors:

```text
Connection timeout       1523
Authentication failed    932
Database unavailable     721
Connection refused       615
Memory error             402
```

Then return:

```text
Top 5 Errors
```

You can use a **HashMap + Min Heap / Priority Queue** here.

That gives you another obvious DSA component without making the project unnecessarily complicated.

---

# 8. Dynamic Operations

Your presentation also emphasizes dynamic updates. 

So implement:

```text
INSERT
DELETE
SEARCH
```

Example:

```text
Insert new log
      ↓
Parse log
      ↓
Insert words → Compressed Trie
      ↓
Insert timestamp → Fusion Tree
      ↓
Immediately searchable
```

You don't need complicated distributed synchronization for a capstone.

---

# 9. Benchmarking

This is where you prove that your DSA implementation works.

Run:

```text
10,000 logs
50,000 logs
100,000 logs
500,000 logs
1,000,000 logs
```

Measure:

### Search

```text
Keyword search
Prefix search
Time-range search
```

### Updates

```text
Insertion time
Deletion time
```

### Resources

```text
Memory usage
```

Then make simple graphs.

For example:

```text
Dataset Size → Search Time
Dataset Size → Memory Usage
Dataset Size → Insertion Time
```

You don't need to beat Splunk.

Just compare your structures against **simpler alternatives**, such as:

```text
Linear Search
Standard Trie
Hash Table
BST
Your Hybrid Structure
```

---

# The Complete Workflow

So your actual implementation can be:

```text
                 START
                   │
                   ↓
            Load / Generate Logs
                   │
                   ↓
             Parse Log Records
                   │
                   ↓
          ┌────────┴────────┐
          ↓                 ↓
    Extract Text       Extract Numeric
          │                 │
          ↓                 ↓
 Compressed Trie       Fusion Tree
          │                 │
          └────────┬────────┘
                   ↓
             Query Interface
                   │
        ┌──────────┼──────────┐
        ↓          ↓          ↓
      Text       Range       Top-K
     Search      Filter      Errors
        │          │          │
        └──────────┼──────────┘
                   ↓
              Display Result
                   │
                   ↓
              Benchmark
                   │
                   ↓
                  END
```

## Keep your team divided like this

Since you have **4 members**, a very clean division would be:

| Member | Responsibility                         |
| ------ | -------------------------------------- |
| **1**  | Compressed Trie implementation         |
| **2**  | Fusion Tree implementation             |
| **3**  | Log processing + Query Engine + Top-K  |
| **4**  | UI + Dataset generation + Benchmarking |

Then everyone has a clear DSA contribution.

### Most importantly

I would **not** add Kafka, Kubernetes, cloud deployment, distributed databases, machine learning, Docker clusters, etc. unless your faculty specifically requires them.

For a DSA capstone, the impressive part should simply be:

> **“We designed a log search engine where different DSA structures are selected for different types of queries, and we experimentally compare their performance.”**

That's a very manageable and defensible project.
