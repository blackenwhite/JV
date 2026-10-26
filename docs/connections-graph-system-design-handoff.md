# Connections graph system design — handoff

**Status: LOCKED through Deep dive 4 (freshness after connect and disconnect) and the interview answer keys. All four planned deep dives are decided. Edge storage stays inside deep dive 1.**

**Do not edit, append, rewrite, rename, or delete this file unless Nabajyoti explicitly gives permission in the current request to update this handoff.** Reading it and continuing the interview in chat are allowed. New decisions stay in the conversation until Nabajyoti gives that permission.

Interview target: Senior Software Engineer, LinkedIn-style, 45 minutes. Use a Hello Interview progression: naive, then better, then interview-ready. Proceed one step at a time and do not dump the complete design.

Priority among Nabajyoti’s collected questions: #3.

---

## Where we are

| Phase | Step | State |
| --- | --- | --- |
| 0 | Understand the question | Locked |
| 1 | Functional requirements | Locked |
| 1 | Non-functional requirements | Locked |
| 1 | Core entities | Locked |
| 1 | APIs | Locked |
| 2 | Basic working system | Locked. Naive baseline. Does not meet the NFRs. |
| 3 | Deep-dive shortlist | Locked |
| 3 | Deep dive 1 — degree within 200 ms | Locked. Includes the graph store. |
| 3 | Deep dive 2 — People You May Know | Locked. Background top 100, page is a lookup. |
| 3 | Deep dive 3 — storing the edges | Absorbed into deep dive 1. Do not reopen the database choice. |
| 3 | Deep dive 4 — freshness after connect and disconnect | Locked. Patch the two 1st-degree caches. Do not fan out. |
| Final | Interview answer keys and overall architecture | Locked |

**Next chat step:** No required deep dive remains. Continue only with a recap, interview practice, or an optional follow-up that Nabajyoti requests. `next` alone does not open a new topic.

---

## Coaching rules

- Never give the complete design in one response.
- Move exactly one step when Nabajyoti says `next`.
- If Nabajyoti asks a question, answer only that question and do not advance automatically.
- Keep replies concise, normally about 150–250 words.
- Introduce at most one or two new technical concepts per deep-dive response.
- Evolve the same system: naive → better → interview-ready.
- For every improvement, state what is wrong, why it matters, the simplest fix, and the trade-off.
- Optimize for a defensible Senior SWE design in a 45-minute interview, not maximum sophistication.
- Do not introduce a technology because it is popular. The graph store is decided: JanusGraph on Cassandra or Scylla. Kafka and Redis were not added. Do not rename the store to LIquid, and do not switch it to Neo4j.
- Commands such as `next`, `recap`, `deeper`, or `interview answer` are not permission to modify this file.

Commands retain their agreed meanings: `next`, `deeper`, `simplify`, `why`, `alternatives`, `interview answer`, `recap`, `big picture`, `skip`, and `challenge me`.

---

## Question in plain language

Design the service that owns LinkedIn’s professional graph: who is connected to whom, and the two features that read that graph.

A **connection** is an edge between two people. It is mutual. **1st degree** means a direct connection. **2nd degree** is a friend of a friend. **3rd degree** is one hop further. **Out of network** means no path within three hops.

```text
You —— Alice —— Bob —— Carol
     1st      2nd      3rd
```

Two user-facing jobs sit on this graph:

1. **Relationship queries.** “Is Carol my 1st, 2nd, or 3rd degree?” and “who are our mutual connections?”
2. **People You May Know.** Suggest people you are not connected to yet, mostly from the 2nd-degree neighborhood.

### System boundary

**In scope.** Storing connections, updating them when people connect or disconnect, answering degree and mutual-connection questions, and producing suggestions.

**Out of scope.** Profiles, search, feed, messaging, blocking, and the pending-invite product (send, withdraw, ignore). An edge appears only when a connection is accepted. “Why you may know this person” beyond a mutual-connection count is also out of scope. Other products call this service for graph facts. This service does not own member profiles. It stores member IDs only.

### Central engineering challenge

One graph, two workloads. Degree checks must be fast and correct on a graph of hundreds of millions of people. Suggestion generation walks huge neighborhoods, so that work cannot run as a fresh graph walk on every page view.

Treat “how are we connected?” as an online query. Treat “who should I meet?” as a heavier computation prepared ahead of time.

---

## Locked functional requirements

1. **Connect and disconnect.** Two members become 1st-degree connections, or an existing connection is removed. This is the only way the graph changes.
2. **Degree of separation.** Given the viewer and another member, return 1st, 2nd, 3rd, or out of network.
3. **Mutual connections.** Given the viewer and another member, return how many people are connected to both, and who they are.
4. **People You May Know.** Return a short ranked list of members the viewer is not connected to yet. The main signal is shared 1st-degree connections.

---

## Locked non-functional requirements

These are interview assumptions, not measured product numbers.

1. **Scale.** About 1 billion members. A typical member has on the order of 100–500 connections, and a few reach the 30,000 cap. Because a connection is one undirected edge, the graph holds tens of billions of edges. Degree checks run on almost every profile view: plan for roughly 10k–100k queries/sec at peak. New connections are much rarer, on the order of hundreds to a few thousand per second at peak.
2. **Latency.** A degree badge or mutual-connection count on a profile must return in under about 200 ms at p99. People You May Know must render in the same budget, so the ranking is already computed when the page loads. A live walk out to the 3rd degree cannot sit on that path.
3. **Freshness.** After you connect or disconnect, your own view of that edge updates within a few seconds. Another member’s degree badge, and your suggestion list, can lag by minutes to hours.
4. **Availability.** Degree lookups stay up even if suggestion generation is delayed. A lost connection is unacceptable. A stale suggestion is acceptable.

**Key decision so far.** Neighborhood explosion shapes the design. Five hundred connections who each have five hundred connections is hundreds of thousands of 2nd-degree people before dedup. Degree stays a small online query. Suggestions are prepared ahead of time.

---

## Locked core entities

The graph service stores two things at this stage of the design. Degree of separation and the mutual-connection list do not have their own records yet. Deep dive 1 later adds a network cache of the viewer’s 1st-degree list and 2nd-degree set. That cache is serving data, separate from these two entities.

### Connection

One undirected edge. Created when two members connect. Deleted when they disconnect. This is the source of truth.

- `member_a_id`, `member_b_id` — the two people. Stored once per pair.
- `connected_at` — when the edge was created.

### Suggestion

One precomputed People You May Know row for a viewer. Safe to rebuild and discard.

- `member_id` — who sees the suggestion.
- `suggested_member_id` — who is being recommended.
- `mutual_count` — how many 1st-degree connections they share. This is the rank signal.
- `rank` — position in that member’s list.
- `generated_at` — when this row was computed.

Deep dive 2 stores the top 100 of these rows in one Cassandra or Scylla partition keyed by `member_id`. That partition is serving data. It is separate from the JanusGraph edges. The page reads it and walks nothing.

```text
member ── Connection ── member
member ── Suggestion (precomputed, rebuildable)
```

---

## Locked APIs

Five endpoints cover the four requirements. The viewer’s member id is on every read. Writes name both people.

**Connect**

`POST /connections`

Request: `{ member_id, other_member_id }`

Response: `{ member_id, other_member_id, connected_at }`

**Disconnect**

`DELETE /connections`

Request: `{ member_id, other_member_id }`

Response: empty success.

**Degree**

`GET /members/{memberId}/degree/{otherMemberId}`

Response: `{ degree }` where `degree` is `1`, `2`, `3`, or `out_of_network`.

**Mutuals**

`GET /members/{memberId}/mutual-connections/{otherMemberId}?limit=10`

Response: `{ count, member_ids }`

`count` is the full number. `member_ids` is a short sample, capped by `limit`.

**People You May Know**

`GET /members/{memberId}/suggestions?limit=20`

Response: `{ suggestions: [{ member_id, mutual_count, rank }] }`

This reads stored `Suggestion` rows. It does not walk the graph.

**API decision.** Endpoint shapes stay as written here. Deep dive 1 is the degree and mutuals implementation. Deep dive 2 is the suggestions implementation: `GET /suggestions` reads the stored top 100 and returns `limit` rows. It does not walk the graph.

---

## Locked basic working system

Small scale only. One Connection Service and one SQL database. This meets every functional requirement and misses the NFRs on purpose. It remains the naive baseline. Deep dive 1 replaces it for degree, mutuals, and the edge store.

```text
Client → Connection Service → SQL
                              connections
                              (member_low_id, member_high_id, connected_at)
```

Each undirected pair is stored once, with the smaller member id first. Connect is an insert. Disconnect is a delete. The writer’s next read sees the new edge because it is the same database.

**Degree.** Load the viewer’s neighbors. If the other person is in that set, return 1. Load neighbors of those people, skipping the viewer and the 1st-degree set. If the other person appears, return 2. One more hop returns 3. If they never appear, return `out_of_network`.

**Mutuals.** Load both neighbor sets. `count` is the size of the overlap. `member_ids` is the first `limit` people in that overlap.

**People You May Know.** Use the 2nd-degree people from that walk, drop anyone already connected to the viewer, count shared neighbors, and return the top `limit`. Suggestion rows stay empty. The request computes the list. Deep dive 2 replaces this for the interview-ready design. The naive version remains the baseline.

**Accepted limitations.** A member with a few hundred connections pulls hundreds of thousands of 2nd-degree rows into one request. The 3rd hop is larger. This misses the 200 ms and peak QPS targets. One server and one database is a single point of failure.

---

## Locked deep-dive shortlist

1. **Degree of separation within 200 ms.** Locked below. The profile badge is the hot path.
2. **People You May Know.** Locked below. The page reads a stored top 100. The list may lag by about an hour.
3. **Storing the edges.** Decided inside deep dive 1. Do not reopen it.
4. **Freshness after connect and disconnect.** Locked below. The two people on the edge see it within the write. Other members’ 2nd-degree caches may lag by about an hour.

No required deep dive remains. Do not open a new topic unless Nabajyoti asks for one.

---

## Locked deep dive 1 — degree within 200 ms

The naive system walks outward from the viewer. That is correct on a small graph and misses the latency budget. A viewer with 500 connections, whose connections each have 500 connections, causes one degree check to read about 250,000 rows before the 3rd hop.

The next step was to load both neighbor lists and intersect them. Degree 1 is membership in the viewer’s list. Degree 2 is a non-empty overlap, and that overlap is also the mutual-connection result. Degree 3 still required opening every neighbor’s list.

A work cap on that 3rd-degree search was considered and rejected. LinkedIn’s published graph-distance system says the badge cannot be approximated, because distance also decides which profiles a member is allowed to see. Returning `out_of_network` for a real 3rd-degree member is the wrong trade-off.

### Final degree answer

Keep a network cache for the viewer. It holds that viewer’s 1st-degree list and their 2nd-degree set, stored as a compressed sorted array. The 2nd-degree set is computed data for viewers who need a badge. It is not built for all 1 billion members, and it does not live inside the graph database. A person with about 500 connections can have a cached 2nd-degree set of a few hundred thousand ids, on the order of a megabyte once stored tightly.

On a profile or search decoration for viewer X and member Y:

- Y is in the 1st-degree list → degree 1.
- Y is in the cached 2nd-degree set → degree 2.
- Otherwise load Y’s 1st-degree list and intersect it with X’s cached 2nd-degree set. One shared person → degree 3. No shared person → `out_of_network`.

Check degree 1 and degree 2 before the intersection. A shorter path would already have matched. An overlap at the last step means the distance is exactly 3. Stop there. The product does not search past three hops.

Mutuals stay the overlap of the two 1st-degree lists. `count` is the size of that overlap. `member_ids` is the first `limit` people in it.

If the viewer has no cached 2nd-degree set, rebuild it from the graph by reading the neighbor lists of their 1st-degree connections and removing duplicates, then run the same checks. That rebuild is the slow path. It can touch many partitions, because those friends are not all on one machine. LinkedIn published that most distance calls were served from this cache.

The request does not run a breadth-first search, and it does not run a live bidirectional search. The 3rd-degree test is the bidirectional idea already stored in the cache: X’s 2-hop set meets Y’s 1-hop list.

If the neighbor list or the cache cannot be loaded, omit the badge and leave the profile up.

### Words to use in the interview

“I would use JanusGraph, with Cassandra or Scylla underneath. The member id is the partition key, and that member’s connections are stored with them. The graph is on the order of 10 TB, so one machine cannot hold it. A replica is a cluster whose combined RAM holds one copy. I cache the viewer’s 1st-degree list and 2nd-degree set. Degree 1 and 2 are membership tests. Degree 3 is the intersection of my cached 2nd degree with the other person’s 1st-degree list. A miss rebuilds that set from the graph. I keep the result exact, because distance also controls who can see the profile.”

### What not to say

- Do not say the database is LIquid. LIquid is LinkedIn’s own graph database. The public system to name is JanusGraph.
- Do not say index-free adjacency. LIquid’s published design uses in-memory hash indexes.
- Do not say one service instance loads the whole graph.
- Do not say Neo4j is the store. Neo4j fits a smaller cluster. This graph needs each member’s adjacency list partitioned across a memory-resident cluster.
- Do not bring up the set-cover paper unless the interviewer asks why a cache miss is slow. The expensive part is gathering neighbor lists from many partitions and removing duplicates.

### Published context, for coaching only

LIquid, from LinkedIn’s engineering posts and the New Stack interview, is an in-memory graph of the Economic Graph: on the order of 270 billion edges, about 2 million queries per second, and a replica of roughly 20–40 machines with a terabyte or more of RAM each. Those edges include companies, skills, jobs, and schools, so that number is larger than the member-to-member graph. A connection a few seconds old is queryable. Use these facts only if Nabajyoti is asked how LinkedIn does it. In the design Nabajyoti defends, the name is JanusGraph.

The degree algorithm follows LinkedIn’s Network Cache Service paper: the source’s 2nd degree is a compressed sorted array, distance is a set intersection, and the result is exact.

---

## Locked graph store

This replaced the single SQL database for the interview-ready design. The Phase 2 SQL system remains the naive baseline.

JanusGraph stores the member and that member’s connections together. Cassandra or Scylla places that partition on one machine. The connection service asks that machine for one neighbor list. The rest of the graph stays where it is.

One replica is a cluster of machines. Together their RAM holds one copy of the member-to-member edges. Additional replicas are further copies, for read throughput and so one machine failure does not remove the graph. Disk holds a snapshot so a restarted machine can load its own partition into RAM. The profile view reads RAM.

Interview estimate, not a measurement. About 1 billion members and an average around 200 connections gives about 100 billion undirected edges, stored in both directions. The neighbor ids alone are about 1.6 TB. With indexes and overhead, plan on the order of 10 TB. A few dozen machines with a terabyte of RAM each can hold one replica. The 30,000 connection cap is a few hundred kilobytes, so one member’s list fits on one machine.

“In memory” means a neighbor-list read is served from RAM. It does not mean one process loaded every edge.

---

## Locked deep dive 2 — People You May Know

The page cannot count mutuals by walking a 2nd-degree neighborhood. A typical member produces on the order of a few hundred thousand candidates. That scan is the same expensive read as a degree-cache miss.

### Final PYMK answer

Build a small stored list in the background. The page is a lookup.

For an active member, a throttled worker reads that member’s 1st-degree list, reads those friends’ neighbor lists from JanusGraph, counts how often each candidate appears, drops the member and anyone already connected, and keeps the **top 100** by mutual count. It writes those rows to one Cassandra or Scylla partition keyed by the member. That partition is separate from the graph edges.

`GET /suggestions` reads that partition and returns 20. It does not touch the graph.

A missing or old list still returns immediately: an empty list, or the previous 100. The worker refreshes that member later. Each member is rebuilt at most about **once an hour**. The worker is capped so suggestion builds stay behind profile-badge traffic. If the worker is down, My Network shows the last list and degree checks keep working.

On the read, drop anyone who is now in the viewer’s 1st-degree list. Someone you just connected with leaves the suggested list without a rebuild.

### Assumptions

- Mutual count is the only rank signal.
- Storing 100 and showing 20 is enough.
- A list that is up to about an hour old is acceptable.
- Only members who open suggestions, plus members marked dirty by a connect or disconnect, are worth rebuilding.
- A member near the 30,000 cap is not fully expanded. The worker stops after about **1 million** edges and ranks from what it saw.

### Trade-off

The list is stale and, for the very large accounts, incomplete. A new mutual can be missing for up to an hour. The page stays inside 200 ms, and a slow rebuild cannot take down the degree badge.

### If they cite the on-graph 50 ms number

LinkedIn’s 2023 LIquid post says People You May Know queries moved onto their graph and landed around an average of 50 ms. A memory-resident graph can answer a bounded mutual-count query online. The full exact count is still the wide scan. The stored top 100 is the path to defend. An online query is only a fallback when no list exists, and it keeps the same edge budget of about 1 million edges.

### Words to use in the interview

“Suggestions are a top 100 by mutual count, computed off the request from neighbor lists we already store, one partition read on the page. I throttle that build to about once an hour per member. The page filters out people I have since connected with.”

---

## Locked deep dive 4 — freshness after connect and disconnect

The graph write is the easy part. The network cache is what makes a new edge look missing.

### Final freshness answer

The connect or disconnect call finishes only after three things succeed:

1. Both neighbor lists are written durably in JanusGraph, one partition for each member.
2. Both members’ cached **1st-degree** lists are patched.
3. Both suggestion lists are marked dirty for the hourly worker.

The call does not rebuild any 2nd-degree set, and it does not fan out to friends.

The writer’s next view of that person is degree 1, because degree 1 is checked before the 2nd-degree set. If the other partition write fails, the retry is safe: the edge is either present or absent. A crash can leave the edge on one side for a moment. The retry completes the other side.

Everyone else’s cached 2nd-degree set is left alone and rebuilt on the normal hourly refresh, using the same exact intersection as deep dive 1. There is no partial walk.

**Example.** Carol is connected to Alice. Alice connects to Bob. Alice and Bob see that edge immediately. Carol’s cached 2nd-degree set does not yet contain Bob, so Carol can miss the new 2nd-degree path until her cache refreshes. That is the lag accepted for people who were not on the edge.

Profile decoration uses the cache. If the badge is instead an allow-or-deny check and that cache is past its hour, rebuild it before deciding.

### Assumptions

- “Within a few seconds” applies to the two people on the edge, through their 1st-degree lists.
- Other badges may lag about an hour.
- A 1st-degree list is small enough to rewrite on the request. A 2nd-degree set is about a megabyte, so it is not edited in place.
- Writes are a few thousand per second, so a dirty flag is enough. Kafka is optional at that rate and was not added. Redis was not added.

### Trade-off

Bystander badges and suggestion lists are stale. Fan-out would make one connect invalidate hundreds of megabyte caches and then rebuild them. Distance math stays exact against whatever set it reads.

### Words to use in the interview

“I write the edge to both members’ partitions and patch their 1st-degree caches before I ack. I leave everyone else’s 2nd-degree cache for the hourly refresh. The distance math stays exact against whatever set it reads. I do not spread one new edge out to every friend.”

---

## Locked overall architecture

```mermaid
flowchart LR
  write[Connect or disconnect] --> graph[JanusGraph: both neighbor lists]
  write --> first[Patch both 1st-degree caches]
  write --> dirty[Mark both suggestion lists dirty]
  profile[Profile badge] --> cache[Network cache]
  cache --> graph
  page[My Network] --> suggestions[Suggestion partition]
  suggestions --> filter[Drop current 1st-degree ids]
  worker[Hourly throttled worker] --> graph
  worker --> suggestions
```

```text
Profile and search ──→ Connection service ──→ Network cache
                                              (1st-degree list and 2nd-degree set)
                                                    │
                                                    └─ cache miss, or an expired cache
                                                       used for an allow-or-deny check,
                                                       rebuilds the 2nd-degree set

My Network ──→ Suggestion partition ──→ drop anyone now in the 1st-degree list

Connect or disconnect
  ├─→ JanusGraph: durable write of both neighbor lists
  ├─→ patch both 1st-degree caches
  └─→ mark both suggestion lists dirty

Hourly throttled worker
  ├─→ reads neighbor lists from JanusGraph
  ├─→ writes the top 100 suggestions
  └─→ refreshes 2nd-degree sets for active members

JanusGraph
  one member's neighbors on one partition
  cluster RAM holds one copy of the edges
  disk snapshot reloads a restarted machine
```

Degree and mutuals use the cache and, when needed, one neighbor-list read from JanusGraph. Suggestions use the stored top 100. A connect or disconnect updates the two edge partitions and the two 1st-degree caches before it returns.

---

## Interview answer keys

Say these aloud. The detail behind each one is in the locked deep dive above.

**Degree and the graph store.** “I would use JanusGraph, with Cassandra or Scylla underneath. The member id is the partition key, and that member’s connections are stored with them. The graph is on the order of 10 TB, so one machine cannot hold it. A replica is a cluster whose combined RAM holds one copy. I cache the viewer’s 1st-degree list and 2nd-degree set. Degree 1 and 2 are membership tests. Degree 3 is the intersection of my cached 2nd degree with the other person’s 1st-degree list. A miss rebuilds that set from the graph. I keep the result exact, because distance also controls who can see the profile.”

**People You May Know.** “Suggestions are a top 100 by mutual count, computed off the request from neighbor lists we already store, one partition read on the page. I throttle that build to about once an hour per member. The page filters out people I have since connected with.”

**Freshness.** “I write the edge to both members’ partitions and patch their 1st-degree caches before I ack. I leave everyone else’s 2nd-degree cache for the hourly refresh. The distance math stays exact against whatever set it reads. I do not spread one new edge out to every friend.”

**If they push on the 50 ms on-graph PYMK number.** “A memory-resident graph can answer a bounded query online. I still serve a stored top 100, and I only run an online query when that list is missing, with a cap of about a million edges.”

**If they push on a stale badge.** “Ordinary decoration can be about an hour behind for people who were not on the new edge. If that badge allows or denies access and the cache is past its hour, I rebuild it first.”

---

## Not decided yet

Leave these alone unless Nabajyoti asks. They are optional for the 45-minute interview.

- Replication factor, snapshot format, and the exact repair path after a machine dies.
- A queue. The locked design uses a dirty flag and a throttled worker. Kafka is not part of the design.
- Redis. The 1st-degree list, the 2nd-degree set, and the suggestion partition are the serving copies. Redis was not added.
