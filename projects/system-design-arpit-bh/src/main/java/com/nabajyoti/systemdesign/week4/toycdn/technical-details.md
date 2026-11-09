# Toy CDN — Technical Details

A local prototype of a content delivery network in plain Java. There is no Docker, no CDN vendor, and no extra library. Two processes are enough: an **origin** that owns the files, and an **edge** that stores copies in memory.

**Lab directory:** `projects/system-design-arpit-bh/src/main/java/com/nabajyoti/systemdesign/week4/toycdn/`

This is a learning lab. `X-Cache` and `POST /purge` have no authentication. Do not expose them on a public network.

---

## What we are building

A CDN edge is a reverse proxy with a cache. The browser talks to the edge. The edge talks to origin only when it has no usable copy.

```text
curl
  -> Edge :8081
       cache fresh: serve memory, X-Cache: HIT
       cache stale: GET origin with If-None-Match
         origin 304: keep body, X-Cache: REVALIDATED
         origin 200: replace body, X-Cache: MISS
       cache empty: GET origin, store, X-Cache: MISS
  -> Origin :8090
       owns /index.html and /style.css
```

Origin is the source of truth. Restarting the edge empties the cache. Restarting origin does not empty the edge; a fresh copy can still be served until TTL ends.

---

## Files

| File | Role |
| --- | --- |
| `OriginServer.java` | HTTP server on **8090**. Serves two in-memory resources. Understands `If-None-Match` and can return **304**. |
| `EdgeServer.java` | HTTP server on **8081**. In-memory `HashMap` cache, TTL, revalidation, purge. |
| `technical-details.md` | This file. |

Package: `com.nabajyoti.systemdesign.week4.toycdn`

---

## Prerequisites

- JDK 23 (this Gradle project already uses Java 23)
- `curl`
- Run `OriginServer` and `EdgeServer` from the IDE (two run configurations), or compile them with the rest of `system-design-arpit-bh`

No AWS account. No Docker. Caching uses a `HashMap`. HTTP uses `com.sun.net.httpserver.HttpServer` inbound and `java.net.http.HttpClient` outbound.

---

## Locked choices

- Origin port: `8090`
- Edge port: `8081` (optional program argument to change it)
- Resources: `/index.html` and `/style.css` only
- `Cache-Control: public, max-age=30` so expiry is easy to watch
- Cache key: request path only (no host, no query string)
- `ETag`: `" + Integer.toHexString(Arrays.hashCode(body)) + "` — a toy fingerprint, not a content hash used in production
- `X-Cache` values: `HIT`, `MISS`, `REVALIDATED`, `NONE`
- Purge: `POST /purge?path=/index.html` on the edge, no auth
- No second edge and no router (see [Out of scope](#out-of-scope))

---

## How the pieces fit

### Origin

Origin holds the real bytes. It sets:

- `Content-Type` — what the bytes are
- `ETag` — fingerprint of the current body
- `Cache-Control: public, max-age=30` — caches may store this for 30 seconds

If the request has `If-None-Match` and it equals the current `ETag`, origin answers **304 Not Modified** with an empty body (`sendResponseHeaders(304, -1)`). Otherwise it answers **200** and the full body.

Origin does not cache. The same URL twice is two `200`s unless the caller sent a matching `If-None-Match`.

### Edge cache

The value in the map is a full HTTP response, not “a file”:

- status (`200` or `404` can both be stored)
- `Content-Type`
- `ETag`
- `Cache-Control`
- body (`byte[]`)
- `expiresAtMillis` — now + `max-age` when the object was filled
- `revalidated` — only used to set `X-Cache: REVALIDATED`

**Fresh:** `now < expiresAtMillis`. Serve from memory. Do not call origin. `X-Cache: HIT`.

**Stale or missing:** call origin. If a stale object still has an `ETag`, send `If-None-Match`. Do not delete the stale object first; without it there is nothing to revalidate.

- Origin **304**: keep the old body, move `expiresAtMillis` forward, `X-Cache: REVALIDATED`. The caller of the edge still gets **200** and the HTML. 304 is only between edge and origin.
- Origin **200**: replace the map entry, `X-Cache: MISS`.

Expiry is checked on the next GET. There is no background timer that deletes keys.

### Purge

TTL is a timer. A publish is an event. While a copy is fresh, the edge does not ask origin, so a new file on origin can stay invisible until `max-age` ends.

`POST /purge?path=/index.html` deletes that key. The next GET is a miss.

Browsers do not call purge. In production this is an authenticated control API used by a CMS, a deploy job, or ops after origin has already changed. This lab skips auth.

`GET /index.html` is still GET-only. Purge lives under `/purge` so a crawler cannot empty the cache by fetching a page.

---

## How to run

1. Start `OriginServer` (`main`). Console: `Origin listening on http://localhost:8090`.
2. Start `EdgeServer` (`main`, no args → port 8081). Console: `Edge listening on http://localhost:8081`.
3. Use `curl` against `8090` (origin) or `8081` (edge).

Register HTTP contexts, then call `server.start()`. The edge should register `/purge` and `/`. The JDK server matches the longest prefix, so `/purge` does not fall through to the GET cache handler.

---

## Walkthrough

### 1. Origin

```bash
curl -i http://localhost:8090/index.html
curl -i http://localhost:8090/style.css
curl -i http://localhost:8090/missing.txt
```

First two: `200`, `Content-Type`, `ETag`, `Cache-Control`. Third: `404`. No `X-Cache`.

Optional 304 against origin alone. Copy the `ETag` value including quotes:

```bash
curl -i -H 'If-None-Match: "paste-etag-here"' http://localhost:8090/index.html
```

Matching tag: `304` and no HTML. Wrong tag: `200` and HTML.

### 2. Miss

```bash
curl -i http://localhost:8081/index.html
```

`200`, HTML, `X-Cache: MISS`.

### 3. Hit (within 30 seconds)

```bash
curl -i http://localhost:8081/index.html
```

Same body, `X-Cache: HIT`. Origin is not asked.

Optional proof: stop origin, repeat the curl before 30 seconds. Still `200` and `HIT`. Start origin again before the later steps.

### 4. Second path

```bash
curl -i http://localhost:8081/style.css
curl -i http://localhost:8081/style.css
```

First `MISS`, second `HIT`. Each path is its own cache key.

### 5. Revalidation (wait more than 30 seconds)

```bash
curl -i http://localhost:8081/index.html
```

`X-Cache: REVALIDATED`, status **200**, same HTML. The client must not see `304`.

### 6. Purge (does not wait for TTL)

```bash
curl -i http://localhost:8081/index.html
curl -i -X POST "http://localhost:8081/purge?path=/index.html"
curl -i http://localhost:8081/index.html
```

POST body: `purged /index.html` (or `no cache entry` if it was already gone). Quote the URL; `?` is special in the shell.

GET after purge: `X-Cache: MISS` even inside 30 seconds.

`/style.css` is unchanged if you did not purge it.

---

## If something does not match

| What you see | Likely cause |
| --- | --- |
| Every edge GET is `MISS` | Calling `:8090` instead of `:8081`, or the edge process was restarted |
| Never `HIT` | GET path not reading the map, or `max-age` parsed as `0` |
| After waiting, `MISS` instead of `REVALIDATED` | Stale entry was removed before the origin call, so no `ETag` was sent |
| After purge, still `HIT` | Query `path` does not match the key. Use `/index.html` with the leading slash |
| `404` on `/purge` | Context not registered, or GET used instead of `POST` |

---

## Out of scope

These were left out on purpose:

- **Second edge.** Each process has its own `HashMap`. A HIT on A is still a MISS on B.
- **Router / DNS.** Production uses DNS or anycast to pick a nearby edge. A Java process on `:8080` hashing `X-Client-Id` would be a stand-in. Not required for this lab.
- **Request collapsing.** Two concurrent misses for the same path can both hit origin.
- **Thread-safe map.** `HashMap` is enough to see HIT/MISS. Use `ConcurrentHashMap` if you stress it.
- **Size limits, LRU, compression, HTTPS.**
- **Auth on purge.** Production purge is a control-plane API, not a public GET.

---

## Ideas this lab is meant to leave you with

1. Origin holds truth. The edge holds copies.
2. `max-age` answers: may I serve this without asking origin?
3. After that, `ETag` + `If-None-Match` answers: is my copy still the right bytes? A `304` avoids downloading the body again.
4. Purge answers: forget this key now. The site owner (CMS, deploy, ops) calls it, not the end user. The cache cannot know that origin just changed.
5. Versioned URLs (`/app.a1b2c3.js`) avoid many purges for static assets. Stable URLs (`/`, article permalinks) still need TTL and sometimes purge.
