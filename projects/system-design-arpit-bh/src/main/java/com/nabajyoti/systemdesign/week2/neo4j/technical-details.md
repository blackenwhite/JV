# Neo4j ingest and pagination

A local prototype that loads a small follows-graph into Neo4j, then pages one person's follow list two ways.

- Graph: `(:Person {id, name})` and `(:Person)-[:FOLLOWS]->(:Person)`.
- Data: 2,000 people. Person `1` follows persons `2` through `51` (50 edges).
- Sort key: `Person.id`, which is unique. A cursor needs a unique order.
- Java talks to Neo4j over Bolt (`localhost:7687`). The browser UI is `localhost:7474`.

## Steps

### 1. Start Neo4j

- `docker-compose.yml` runs Neo4j Community 5.26 as `neo4j-pagination`.
- Port `7474` is the browser. Port `7687` is Bolt.
- `NEO4J_AUTH` sets the login to `neo4j` / `password`.

### 2. Connect from Java

- The Neo4j Java driver is added in `build.gradle`.
- `GraphDatabase.driver` opens a connection pool to Bolt.
- `verifyConnectivity` fails immediately if the database is down.
- A `Session` is one unit of work. `try` closes the session and the driver when `main` finishes.

### 3. Unique constraint on `Person.id`

- `CREATE CONSTRAINT person_id ... REQUIRE p.id IS UNIQUE` runs inside `executeWrite` before any people are written.
- The constraint is the identity of a person and the index later lookups and ordered reads use.
- `IF NOT EXISTS` makes a second run of `main` safe.
- Check with `SHOW CONSTRAINTS`.

### 4. Ingest people in batches

- `ingestPeople(session, 2000, 500)` writes four batches: `1–500`, `501–1000`, `1001–1500`, `1501–2000`.
- Each batch is its own `executeWrite`.
- `UNWIND $ids` turns the id list into one row per id. `$ids` is a parameter, not text pasted into the query.
- `MERGE` on `id` creates a missing person and, on a rerun, updates the name instead of inserting a duplicate.
- Check with `MATCH (p:Person) RETURN count(p)` — expect `2000`.

### 5. Ingest follows edges

- `ingestFollows(session, 1, 2, 51)` adds 50 `FOLLOWS` edges from person `1`.
- `MATCH` finds people that already exist. It does not create them.
- `UNWIND` pairs person `1` with each target id. `MERGE` on the relationship creates each edge once.
- Check with `MATCH (:Person {id: 1})-[r:FOLLOWS]->() RETURN count(r)` — expect `50`.

### 6. Offset pagination (`SKIP` / `LIMIT`)

- `pageByOffset` reads person `1`'s follow list, ordered by `other.id`, inside `executeRead`.
- Page size is 10. `SKIP` is `page * size`, so pages 0, 1, and 2 return ids `2–11`, `12–21`, and `22–31`.
- `SKIP` still finds the skipped rows and discards them. A deep page repeats that work on every request.
- An insert into an earlier page shifts later pages, so a person can repeat or disappear between clicks.
- `PROFILE` with `SKIP 40` shows the engine walking past the skipped rows.

### 7. Cursor pagination

- `pageByCursor` keeps rows with `other.id > $cursor`, ordered by `other.id`, limited to the page size.
- The first cursor is `0` because ids start at `1`. Each next cursor is the last id returned.
- The client stores the cursor. Neo4j does not remember the page.
- Five pages cover ids `2` through `51`. The following call returns an empty page and the loop stops.
- `PROFILE` with `WHERE other.id > 40` is a range on `id`, not a discard of earlier matches.
- The sort key must be unique. Sorting only by name can skip or repeat ties. A name sort needs `ORDER BY name, id`, and the cursor is both values.
