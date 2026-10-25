# IP denylist system design — handoff

**Status: LOCKED through deep dive 5 (fail-open and bad updates).** The gateway decides from an in-memory hash map. Reputation lookups are coalesced to one call per IP, spent on repeat IPs, busiest first. A small `malicious` IP is promoted for about 10 seconds and pulled by every gateway every 2 seconds. A large-share IP is not auto-blocked. Unknown IPs fail open. No required deep dive remains.

**Do not edit, append, rewrite, rename, or delete this file unless Nabajyoti explicitly gives permission in the current request to update this handoff.** Reading it and continuing the interview in chat are allowed. New decisions stay in the conversation until Nabajyoti gives that permission.

Interview target: Senior Software Engineer, LinkedIn-style, 45 minutes. Use a Hello Interview progression: naive, then better, then interview-ready. Proceed one step at a time and do not dump the complete design.

---

## Where we are

| Phase | Step | State |
| --- | --- | --- |
| 0 | Understand the question | Locked |
| 1 | Functional requirements | Locked. Enforcement is the API gateway fleet. |
| 1 | Non-functional requirements | Locked |
| 1 | Core entities | Locked |
| 1 | APIs | Locked |
| 2 | Basic working system | Locked as the naive baseline. The exact prompt below is the source of truth where they differ. |
| 3 | Deep-dive shortlist | Locked. |
| 3 | Deep dive 1 — reputation budget | Locked. The hot path below was replaced by deep dive 2. |
| 3 | Capacity estimates | Locked. Interview assumptions, not measured numbers. |
| 3 | Deep dive 2 — sub-millisecond check | Locked. Exact hash map on the gateway. No Bloom filter. |
| 3 | Deep dive 3 — which IPs to look up | Locked. Repeat IPs only, busiest first. One-shot IPs are never asked. |
| 3 | Deep dive 4 — propagation | Locked. Every gateway pulls the live malicious set from Redis every 2 seconds. No gossip. |
| 3 | Deep dive 5 — fail-open and bad updates | Locked. Fail open for unknown IPs. Auto-block only a small share. Freeze known blocks when reputation is down. |

**Next chat step:** No required deep dive remains. Continue only with a recap, interview practice, or an optional follow-up that Nabajyoti requests. `next` alone does not open a new topic. `next` is not permission to edit this file.

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
- The reputation system is given and cannot be modified. Do not design the detector, and do not add bulk export, push, batch, or any new API on that system.
- Enforcement stays on the API gateway unless Nabajyoti moves it.
- Where earlier sections conflict, follow the current design after deep dive 5. The request never reads Redis and never uses a Bloom filter. Lookups are coalesced: one in-flight call per IP, then no further call until that answer expires. Which IPs earn that call is the deep dive 3 rule, not “every missing IP.” Other gateways learn a new block from the 2-second pull. A large-share IP is not auto-blocked. Unknown IPs are allowed.
- Explain in plain, connected sentences. Nabajyoti asked for simple language rather than punchy fragments.
- Commands such as `next`, `recap`, `deeper`, or `interview answer` are not permission to modify this file.

Commands retain their agreed meanings: `next`, `deeper`, `simplify`, `why`, `alternatives`, `interview answer`, `recap`, `big picture`, `skip`, and `challenge me`.

---

## Question in plain language

LinkedIn sees a huge volume of requests. A small share is malicious. `isMalicious(request)` already exists, so detection is not the problem. The system remembers those source IPs and blocks them from a current denylist.

The prompt says each of the many web servers should have an up-to-date copy of the list. We placed that copy on the API gateway fleet instead. In the interview, say the prompt’s web servers are the external entry points: every gateway holds the list, and bad traffic is dropped before it uses app capacity.

### System boundary

**Actors.** API gateways, which enforce the list. App servers, which see only allowed traffic. Security operators, who correct and explain decisions. Members never see this system.

**In scope.** Record a malicious IP, keep one shared denylist, get that list onto every gateway, and reject later requests from those IPs at the gateway. Operators can correct a decision and look up why an IP was blocked.

**Given, not designed.** `isMalicious(request)`.

**Out of scope.** How `isMalicious` works, reputation scores, per-region or per-application policy scopes, and volumetric DDoS scrubbing. Scopes appear in the common Hello Interview writeup of this problem. We left them out because they add distribution complexity a 45-minute design does not need. Pull them in only if Nabajyoti asks.

A request that reaches an app server without passing the gateway is unchecked.

### Central engineering challenge

The block check sits on the hot path of almost every request, so it has to be local to the gateway and extremely fast. The list changes whenever any gateway sees a new bad IP, and every other gateway has to learn that within seconds, without a bad update taking the site down.

This is a data-plane versus control-plane problem: block locally in well under a millisecond, and distribute denylist changes safely and quickly.

---

## Exact interview prompt

This prompt is the source of truth for the deep dives. Nabajyoti supplied it after Phase 2.

A public-facing API platform receives about **100,000 requests per second** at peak. Before any request is processed, it must decide whether the caller’s IP is malicious and reject the request if it is.

An internal reputation system already does that classification. It accepts one IP and returns `true` (malicious) or `false` (clean). Its owners have fixed the constraints:

- The system cannot be modified.
- There is no bulk export of the blocklist.
- There is no push or webhook for updates.
- There is no batch endpoint.
- There is no way to add new APIs.
- Each lookup takes **10–50 ms**.
- It is shared with other teams, so it has finite capacity. The new service must not exhaust it.

**What this changes.** Malicious versus clean comes only from that single-IP lookup. The platform cannot download the list or subscribe to changes. A lookup on the request path misses the 1 ms budget at 100,000 RPS and can exhaust the shared system. The gateway still enforces. Our own cache may sit in front. The reputation system remains untouched.

The Phase 2 baseline, which runs `isMalicious` on every request and polls a full denylist we publish, is the naive system these deep dives improve. Do not treat that poll, or a bulk read of the reputation system, as already acceptable.

---

## Locked functional requirements

1. **Denylist a malicious IP.** When `isMalicious(request)` is true, add that source IP to the denylist with a reason and a TTL so it expires.
2. **Block at the gateway.** Each API gateway checks the client IP and rejects the request if that IP is currently denylisted. App servers do not perform this check.
3. **Keep every gateway current.** A denylist change reaches all gateways, so they enforce the same list.
4. **Correct and explain a decision.** An operator can remove an IP or add an allow override, and can look up why an IP was blocked.

Sub-millisecond checks, surviving a control-plane outage, and safe rollout are non-functional requirements, not extra features.

---

## Locked non-functional requirements

These are interview assumptions, not measured product numbers.

1. **Check latency.** A block decision finishes in under 1 ms, using data already on that gateway. A remote lookup on the request path cannot meet this.
2. **Freshness.** A new denylisted IP is enforced on every gateway within about 10 seconds. Gateways may briefly disagree. One extra request from a bad IP is acceptable. Slowing every request is not.
3. **Safety.** If the publisher is down, each gateway keeps enforcing its last good list. A bad publish must be reversible. A missing or corrupt list must not block normal users.
4. **Scale shape.** On the order of hundreds of thousands of requests per second across the gateway fleet. Malicious traffic is a small fraction. The denylist may reach millions of IPs. The per-request check is an in-memory lookup, and we cannot ship the full list to every gateway on every new IP.

**Key decision so far.** Fast local reads on the gateway, a list that can be a few seconds stale, and a denylist failure must never become a LinkedIn outage.

---

## Locked core entities

### IPRule

One decision about one IP. An allow and a block are the same record with a different action, so a false positive is another write.

- `ip`
- `action` — `block` or `allow`. Allow wins if both exist.
- `reason` — `isMalicious` result, or an operator note. This is what we show when someone asks why an IP was blocked.
- `expiresAt` — the TTL. A missing expiry means the rule stays until removed.
- `addedBy` — detecting gateway, or operator id.

### ListVersion

One published generation of the rules. This is how a gateway knows whether its copy is current, and how a bad publish is undone.

- `version` — monotonic id
- `publishedAt`
- `checksum` — detect a corrupt copy
- `status` — `active` or `rolled back`

A per-request block log is optional. The reason already lives on `IPRule`, which is enough to explain a decision in this interview. It is not a core entity yet.

---

## Locked APIs

The block check is a local function, `isBlocked(ip)`, against the copy already in the gateway’s memory. Good traffic never calls the network for this check. The APIs below are the control plane.

### POST /v1/rules

A gateway reports a malicious IP, or an operator adds a block or an allow.

- Body: `ip`, `action` (`block` | `allow`), `reason`, `ttlSeconds`, `addedBy`
- Response: `ip`, `action`, `expiresAt`, `version`

The gateway calls this only after `isMalicious` returns true.

### DELETE /v1/rules/{ip}

An operator clears that IP.

- Response: `version`

### GET /v1/rules/{ip}

Why this IP is blocked or allowed.

- Response: `action`, `reason`, `expiresAt`, `addedBy`, `version`
- If both actions exist, the response shows `allow`.

### GET /v1/denylist?afterVersion=

A gateway fetches the rules it does not have yet.

- Response: `version`, `checksum`, `status`, and the rules after that version.

### POST /v1/denylist/rollback

Body: `{ toVersion }`. Republishes an older generation.

- Response: `version`, `status`

---

## Locked basic working system

LinkedIn’s app servers stay as they are. We add one Denylist service and one database. Each API gateway holds the current rules in a hash map.

```text
User → API gateway → local rule map
                 → app servers, only if allowed
                 → Denylist service → database
Operator → Denylist service
```

1. **Block.** Every request hits the gateway. `isBlocked(ip)` reads the local map. `allow` wins. A block is rejected at the gateway.
2. **Denylist.** The gateway then runs `isMalicious`. On true, it adds the IP to its own map, calls `POST /v1/rules`, and rejects the request. The service stores an `IPRule` and a new `ListVersion`.
3. **Stay current.** Every few seconds each gateway calls `GET /v1/denylist?afterVersion=` and merges the result. Rollback stores an older version, and the next poll picks it up.
4. **Correct and explain.** The operator uses `POST` with `allow`, `DELETE`, and `GET /v1/rules/{ip}` on the Denylist service.

This is functionally correct at small size. It is the naive baseline. Deep dive 1 replaced the request path. Do not defend the poll loop or a per-request reputation call as the current design.

### Limitations of this baseline

Deep dive 1 closed the first three for the request path. The last two are still open.

- The baseline runs `isMalicious` on every request. Deep dive 1 moved that call off the normal request.
- One Denylist service was the only source of new rules. Deep dive 1 uses memory plus Redis, with a budgeted reputation lookup on a miss.
- The baseline poll is not the current sync path. Deep dive 1 shares answers through Redis.
- A bad version on a quiet IP can still block for about 10 seconds. Deep dive 5 stops a large-share IP from auto-blocking, and an operator allow removes a block before the TTL.
- Traffic that bypasses the gateway is unchecked.

---

## Locked deep-dive shortlist

Ranked for this prompt. Topics are locked. All five deep dives are solved below. No required deep dive remains.

1. **Stay inside the reputation budget.** Locked below. Almost every request is decided from gateway memory. A live lookup on every request misses 1 ms and can exhaust the shared system.
2. **Sub-millisecond check.** Locked below. The gateway uses an exact hash map. A Bloom filter is not part of the design. Hello Interview asks about Bloom-filter false positives; the locked answer is that we do not use one.
3. **Learn IPs with only single lookups.** Locked below. The budget is spent on repeat IPs, busiest first, and a `true` is promoted for about 10 seconds. Building a detector from traffic logs is the wrong center, because the reputation system already classifies.
4. **Reach every gateway within about 10 seconds.** Locked below. The learning gateway blocks immediately and writes Redis. Every gateway pulls the live malicious set every 2 seconds. A failed pull keeps the previous map. Gossip is not used. Hello Interview’s second follow-up.
5. **Fail-open versus fail-closed, and a bad update.** Locked below. Unknown IPs are allowed. Only a small share of a gateway’s traffic is auto-blocked. Known blocks are kept while reputation is down. Hello Interview’s fourth follow-up.

Fold these in if the interviewer pushes. They are not separate deep dives:

- A cache stampede on a hot IP. Coalescing makes it one lookup for that IP, then nothing until the answer expires about 10 seconds later. Request rate does not become lookup rate.
- A clean IP that turns bad while the cache still says clean. Accepted as about 10 seconds until some lookup notices. Once Redis holds `malicious`, the 2-second pull overwrites a local `clean` on the other gateways.
- One blocked NAT IP standing in for many users. Deep dive 5 does not auto-block an IP that is a large share of that gateway’s traffic. An operator block is required.
- A new gateway starting empty. Missing IPs are allowed. That is fail-open, locked in deep dive 5.

---

## Locked deep dive 1 — reputation budget

### Level 1, naive

The gateway calls the reputation system on every request and waits. The answer is fresh. At 100,000 requests per second that is 100,000 lookups per second, each 10–50 ms, with about 1,000 to 5,000 lookups in flight. A corporate NAT that sends 5,000 clean requests in one second causes 5,000 identical lookups.

### Level 2, better

Each gateway stores `ip → {answer, expiresAt}` in memory for about 10 seconds, for both `malicious` and `clean`. A repeat IP is decided from memory. The first sighting still calls the reputation system. A saved `clean` can be wrong for those 10 seconds if the reputation system would now say `malicious`. Many requests for a new IP can all miss together.

Caching only `malicious` would still look up almost every request, because almost all traffic is clean.

### Level 3, interview-ready

This is the design to defend.

- The gateway decides from a small table in its own memory. A stored answer stays there for about 1 second. The usual check stays under 1 ms.
- If memory is missing the IP, the gateway reads Redis. If Redis has the answer, the gateway copies it into memory and allows or rejects.
- If Redis is missing the IP, one lookup runs for that IP. Other requests for the same IP wait for that answer, so they take 10–50 ms once. The lookup runs only while a fixed budget still has room. The budget is agreed with the reputation owners and is far below 100,000 per second. The exact number is not chosen.
- The answer is saved in Redis for about 10 seconds and copied into memory.
- If the IP is unknown and the budget is used up, the gateway allows the request. An unknown IP is not a known malicious IP. Blocking every unknown IP would block normal users when the cache is empty.
- When a lookup returns `malicious`, that gateway writes Redis immediately. Other gateways see it after their one-second memory entry expires and they read Redis.
- If Redis is down, the gateway keeps using whatever is still in memory. Missing IPs follow the same budget.

Redis finishes a GET in microseconds inside Redis. The gateway still waits on the network, often about 1 ms, longer when Redis is busy. That is why every request does not read Redis.

The reputation system does not send changes. A shared Redis entry of `clean` means no gateway calls again until it expires, so nothing discovers a change at the halfway point by itself. Redis spreads a `malicious` answer only after some lookup has already returned it.

**Accepted trade-off.** A newly malicious IP can be allowed for about 10 seconds, until some lookup notices. Other gateways can be about a second behind after that. An unknown IP is allowed when the budget is spent.

**Interview line.** "I decide from the gateway's memory, so the check stays under a millisecond. Redis shares those answers across gateways. One lookup per IP, plus a small rate limit, protects the reputation system. If I do not know the IP and the budget is spent, I allow the request."

### Current high-level design

```text
Caller → API gateway → app servers, only if allowed
              │
              ├─ memory on that gateway, about 1 second
              │     missing
              ├─ Redis, shared answers, about 10 seconds
              │     missing, and budget left
              └─ reputation system, one lookup for that IP
                    write the answer back to Redis, then copy it into memory
```

1. Memory hit: allow or reject. This is the normal case.
2. Memory miss and Redis hit: copy into memory, then allow or reject.
3. Both miss and budget left: one coalesced reputation lookup, save in Redis for about 10 seconds, copy into memory. `malicious` is rejected. `clean` goes to the app servers.
4. Unknown and budget spent: allow. Do not call the reputation system to refill the cache.
5. Redis down: use whatever memory still has. A missing IP follows the budget.

The Phase 2 Denylist service, database, and poll loop are not the path to defend. Operator allow and remove, `GET /v1/rules/{ip}`, and rollback are still required by the functional requirements, and they are not placed on this picture yet.

Deep dive 2 replaced the request path above. Do not defend a synchronous Redis read, or a reputation call, on the request. The picture to defend is the current design at the end of deep dive 2.

---

## Locked deep dive 2 — sub-millisecond check

### Level 1, naive

The deep dive 1 map is exact and a hit is well under 1 ms. A miss reads Redis. That hop is often about 1 ms, and longer when Redis is busy. Example: `203.0.113.50` sends its first request of the second, memory has no row, and Redis takes 1.5 ms to return `clean`. The answer is right, and the request missed the budget.

### Level 2, better

A Bloom filter of malicious IPs lives on the gateway, and the request is decided from that filter alone. "No" allows. "Yes" rejects. Redis and the reputation system only refresh the filter in the background.

This makes every check local. A false "yes" rejects a clean caller. At a 1% false-positive rate that is about 1,000 good requests a second. This level is not the design to defend.

### Level 3, interview-ready

There is no Bloom filter.

Each gateway keeps an exact hash map in its own RAM. The key is the IP. A bad value rejects the request. A clean value allows it and stops another reputation lookup for about 10 seconds. A missing key allows this request. The lookup stays under 1 ms. The request does not call Redis or the reputation system.

A missing IP is looked up once in the background, and only while the reputation budget has room. The answer is written to Redis for about 10 seconds. If it is bad, this gateway inserts it immediately, so the next request here is rejected. Other gateways learn that block on the deep dive 4 pull, about 2 seconds later.

The first request from an IP we have never checked is allowed. That is a real gap of one request, or a few seconds of requests. Known-bad IPs already in the map are blocked immediately. Waiting to check every stranger would cost 10–50 ms and can exhaust the reputation system. Blocking every stranger would lock out normal new users.

A revert deletes that IP from the map and from Redis. If Redis still says bad, the next refresh writes the block back. The gateway that handled the revert drops it immediately. Other gateways keep blocking until they refresh. A block with a one-minute TTL falls out on its own and the IP becomes missing again.

**Memory.** A fat entry of about 450–500 bytes makes 1 million rows about 500 MB. That is acceptable, and 1 million is the 10-second ceiling, not the normal size. The normal map is about 10,000 IPs, roughly 5 MB at that fat size. The hot map stores the IP and the expiry. The reason stays in Redis for `GET /v1/rules/{ip}`. A slim row is about 50 bytes, so 1 million rows is about 50 MB and 10 million stored rows is about 500 MB. That still fits. It does not lead us to a Bloom filter.

**Accepted trade-off.** A bad IP that is not in the local map yet is allowed until the background lookup inserts it. A cleared IP stays blocked on other gateways until they refresh.

**Interview line.** "The gateway blocks from a hash map in memory, so the check is under a millisecond. A missing IP is allowed once, and I look it up in the background within a small budget. Redis shares those answers with the other gateways."

### Current design

This is the picture to defend. It replaces the deep dive 1 request path.

```text
Caller → API gateway → local hash map
                         bad     → reject
                         clean   → allow → app servers
                         missing → allow → app servers
                                   and, in the background, one reputation lookup if the budget has room
                                         → save the answer in Redis for about 10 seconds
                                         → if bad, add it to this gateway's map immediately

Other gateways pull the live malicious set every 2 seconds. Deep dive 4 is that copy.
```

At 100,000 requests per second: about 15 gateways (5 per zone), one Redis primary and one replica, about 1,000 reputation lookups per second under the repeat assumption, and about 150 app servers with room for a zone loss. Those counts stay the locked capacity estimates. Redis reads are background refreshes. They are not on the request.

Deep dive 3 replaced the rule “look up every missing IP while the budget has room.” The request path above is unchanged. Who earns the background lookup is the current design at the end of deep dive 3.

Operator remove, explain, and rollback are still required and are not on this picture. How a new bad IP reaches every gateway is deep dive 4. What happens when Redis or the reputation system is down is deep dive 5.

---

## Locked deep dive 3 — which IPs to look up

### Level 1, naive

The deep dive 2 gateway asks about every missing IP, in arrival order, while the budget has room. That is fine when new IPs arrive slower than about 1,000 per second. A burst of strangers spends the whole budget on addresses that never return. Example: 20,000 new IPs arrive in one second. The first 1,000 are throwaway scanner addresses and come back `clean`. IP number 1,001 would be `malicious` and keeps sending, but it is never asked about, so every one of its requests is allowed.

### Level 2, better

The first sight of an unknown IP is allowed and only counted in gateway memory. The reputation system is not called. Once that IP comes back, it is eligible, and the budget goes to the eligible IPs seen the most. The 20,000 one-shot scanner addresses take no lookups. The persistent bad IP is asked on a later request. A `malicious` answer is still written into this gateway’s map immediately and saved in Redis.

### Level 3, interview-ready

Level 2 is sufficient. There is no extra component.

- The first request from an unknown IP is allowed, and the gateway only counts it.
- Once that IP comes back, it is eligible for one lookup.
- Each second the budget, about 1,000 lookups, goes to the eligible IPs with the highest counts.
- A `malicious` answer is written into this gateway’s map and into Redis with a TTL of about 10 seconds, so a wrong answer falls off by itself.
- A `clean` answer is remembered for those same 10 seconds, so a busy clean IP is not looked up again.
- An operator allow still wins.
- A person does not approve each IP. That would miss the 10-second freshness bar.

The steady-state estimate of about 1,000 lookups per second still stands. Those are refreshes of IPs that stay active. One-shot addresses no longer consume that budget during a scan.

**Accepted trade-off.** An IP that sends one request, or that keeps changing address, is never learned. If many IPs all repeat, the quieter ones wait behind the busiest. A wrong `malicious` answer can block a repeat IP for about 10 seconds. Other gateways learn that block on the deep dive 4 pull.

**Interview line.** "I cannot download the list, so I spend my lookup budget on IPs that come back, busiest first. One answer promotes that IP for about 10 seconds. One-shot addresses are allowed and never checked."

### Current design

This is the picture to defend. The request path is still the deep dive 2 hash map. Only the background lookup rule changes.

```text
Caller → API gateway → local hash map
                         bad     → reject
                         clean   → allow → app servers
                         missing → allow → app servers
                                   first sight: count it, do not look it up
                                   it comes back: eligible
                                   budget goes to the busiest eligible IPs
                                         → one reputation lookup
                                         → save the answer in Redis for about 10 seconds
                                         → if bad, add it to this gateway's map immediately

Other gateways pull the live malicious set every 2 seconds (deep dive 4).
```

Deep dive 4 is the propagation to defend. What happens when Redis or the reputation system is down, and how a bad publish is stopped, is deep dive 5.

---

## Locked deep dive 4 — propagation

### Level 1, naive

The learning gateway adds the IP to its own map and writes Redis with a TTL of about 10 seconds. Other gateways copy Redis in the background, with no deadline. The learning gateway blocks on its next request. A gateway that reads Redis after the key has expired never learns the block. Example: Gateway 1 writes `198.51.100.20` at t = 0, and the key expires at t = 10. Gateway 2 copies every 30 seconds and next copies at t = 25, so it keeps allowing that IP.

### Level 2, better

Each gateway pulls the live malicious entries from Redis every 2 seconds and overwrites its own map for those IPs. A local `missing` or `clean` becomes `bad`. Clean answers are not copied. Gateway 2 now sees `198.51.100.20` at about t = 2, while the Redis entry still exists. Gateways can disagree for one poll interval. A gateway that is down for the whole 10-second TTL misses that write.

Nabajyoti asked about gossip. It can spread the set, and it is not the design. With about 15 gateways and Redis already holding the answer, gossip’s arrival time depends on which peers talk. The pull has a hard bound of about 2 seconds. Gossip fits a large fleet with no shared store.

### Level 3, interview-ready

Level 2 is sufficient. There is no extra mechanism.

- A `malicious` lookup updates that gateway’s map immediately and adds the IP to the malicious set in Redis, with a TTL of about 10 seconds.
- Every gateway reads that set every 2 seconds.
- A successful read overwrites the local decision for those IPs.
- A failed read keeps the map the gateway already has.
- Fifteen gateways reading one small set every 2 seconds is a light load for the one Redis primary. This is the background refresh. It is not a per-request Redis read.
- The slowest serving gateway applies the block on its next pull, about 2 seconds later. That is inside the 10-second freshness bar.
- A restarted gateway has an empty map. It allows missing IPs. Deep dive 5 is the failure behavior around that empty map and a dead reputation system.

**Accepted trade-off.** For up to about 2 seconds, another gateway can still allow an IP that this gateway already blocks. A block older than the Redis TTL is gone. An active bad IP can be learned again through the budget.

**Interview line.** "The gateway that learns a bad IP blocks it immediately and writes Redis. The other gateways pull that set every 2 seconds. A failed pull keeps the previous map. The slowest gateway is about 2 seconds behind."

### Propagation picture

Deep dive 5 adds the fail-open rules and the large-share guard. The picture to defend is the current design after deep dive 5.

```text
Caller → API gateway → local hash map
                         bad     → reject
                         clean   → allow → app servers
                         missing → allow → app servers
                                   first sight: count it, do not look it up
                                   it comes back: eligible
                                   budget goes to the busiest eligible IPs
                                         → one reputation lookup
                                         → if bad, add it to this gateway's map immediately
                                         → add it to the malicious set in Redis for about 10 seconds

Every gateway, every 2 seconds:
  successful read of that set → overwrite local decisions for those IPs
  failed read → keep the previous map
```

Deep dive 5 is the failure behavior on top of this picture.

---

## Locked deep dive 5 — fail-open and bad updates

### Level 1, naive

A missing IP is allowed. If the lookup budget is spent, the request is allowed. A failed Redis pull keeps the map already on that gateway. While the reputation system is down, requests are still decided from that map, so the platform stays up. An empty gateway does not reject all 100,000 requests per second.

A wrong `malicious` answer has no brake. The learning gateway applies it immediately, and the 2-second pull copies it onto every gateway. The 10-second TTL is the only undo. If that IP stays busy, the budget looks it up again and the block comes back. Example: `203.0.113.10` is a corporate NAT sending about 5,000 requests a second. It wins the budget, reputation wrongly returns `malicious`, and within about 2 seconds every gateway rejects those users. After the TTL the NAT is still the busiest IP, so the next lookup blocks it again.

### Level 2, better

When a lookup returns `malicious`, the gateway looks at that IP’s share of its own traffic. A small share is blocked locally and written to Redis, and the other gateways pick it up on the 2-second pull. A large share, on the order of 1% or more of that gateway’s requests, is not blocked and is not written to Redis. The gateway keeps allowing it and records it for an operator. The operator block is what turns that into a real block.

The NAT in the example is about half of one gateway, so the wrong answer never spreads. A quiet bad IP, around 10 requests a second on that gateway, still blocks on its own. A busy IP that really is malicious stays allowed until an operator confirms it. A wrong answer on a quiet IP can still block for about 10 seconds.

### Level 3, interview-ready

The traffic guard covers a bad update. The 10-second TTL would still delete known blocks while the reputation system is down, so the last good list would disappear. The freeze below closes that gap.

- Stay fail-open. A missing IP is allowed, including on an empty gateway.
- Auto-block only when the IP is a small share of that gateway’s traffic.
- A large share, on the order of 1% or more, is recorded and left allowed until an operator blocks it.
- The 2-second pull uses the same rule, so a bad Redis payload cannot block that busy IP either.
- While reputation lookups are failing, gateways pause new lookups and keep the malicious entries they already have. Missing IPs stay allowed.
- A failed Redis read keeps the previous map.
- When reputation recovers, the 10-second TTL applies again, so a wrong block on a quiet IP ages out.
- An operator allow removes an IP from the set before that TTL.

**Accepted trade-off.** A busy malicious IP stays allowed until an operator blocks it. A quiet wrong block can last about 10 seconds.

**Interview line.** "I fail open. An unknown IP is allowed, and I only auto-block an IP that is a small share of the gateway’s traffic. A busy IP waits for an operator. If reputation is down, I keep the blocks I already have and stop asking. A bad block on a quiet IP expires in about 10 seconds, and an operator allow removes it sooner."

---

## Current design

This is the picture to defend. It includes coalescing, which is why request volume does not exhaust the reputation system.

```text
Caller → API gateway → local hash map
                         bad             → reject
                         clean           → allow → app servers
                         missing         → allow → app servers
                              first sight: count it
                              it comes back and is among the busiest:
                                   one coalesced reputation lookup
                                   → remember the answer for about 10 seconds
                                   → if bad and a small share of this gateway:
                                        block here, and add it to Redis
                                   → if bad and a large share:
                                        keep allowing, record it for an operator

Every gateway, every 2 seconds, pulls that Redis set.
  success → overwrite those IPs locally, skipping a large share unless an operator blocked it
  failure → keep the previous map

While reputation lookups are failing:
  pause new lookups and keep the malicious entries already in memory
```

**Coalescing.** Many requests for one IP become one lookup. A NAT sending 5,000 requests a second costs one call, then nothing more until that answer expires about 10 seconds later. One-shot addresses are only counted, so a scan of new IPs costs no calls. What remains is distinct IPs that come back. About 10,000 of those are active in a second, refreshed every 10 seconds, which is about 1,000 lookups per second, busiest first. Anything past that cap is allowed. A hot IP cannot blow the budget. A wave of brand-new one-shot IPs cannot either.

**Propagation.** Redis holds the live malicious set for about 10 seconds. The other gateways pull it every 2 seconds. The slowest serving gateway is about 2 seconds behind. Gossip is not used. A failed pull keeps the map that gateway already has.

**Failure behavior.** Unknown IPs fail open. Auto-block only a small share of that gateway’s traffic. A large share waits for an operator. While reputation is failing, pause new lookups and keep known blocks. A wrong block on a quiet IP expires in about 10 seconds. An operator allow removes it sooner.

**Accepted trade-offs.** An IP that shows up once is never learned. A newly bad IP can be allowed until it repeats and wins the budget, then for about 2 more seconds on the other gateways. A busy malicious IP stays allowed until an operator blocks it. A quiet wrong answer can block for about 10 seconds.

**Interview line.** "The gateway blocks from memory. I coalesce by IP, so a hot address costs one reputation lookup per 10 seconds, and I only spend that budget on IPs that come back, busiest first. A small bad IP is written to Redis and the other gateways pull it within 2 seconds. I fail open for anything I do not know, and I will not auto-block an IP big enough to take the gateway down."

---

## Locked capacity estimates

Interview assumptions for the deep dive 1 design at the prompt's peak. The ratios are what to defend.

**Traffic.** 100,000 requests per second. An active IP sends about 10 requests per second and stays on one gateway, so about **10,000 IPs are active in one second**. Malicious traffic is a small fraction, so the app tier still sees about 100,000 requests per second.

**What 10,000 IPs means.** It is the live set in that second. It is not the daily caller count and not the denylist. At 100,000 requests per second the most IPs you can touch in one second is 100,000, and only when every request comes from a different IP. A 10-second Redis window can hold at most about 1,000,000 of them. 10 million is a believable count of unique IPs across a day, or of rows on a stored denylist. Spread across a day, 10 million unique IPs are only about 100 brand-new IPs per second.

**A stored list of 10 million IPs** at about 100 bytes per row is about 1 GB. A fatter object, about 450–500 bytes, is about 5 GB, which is tight. Deep dive 2 keeps the hot map slim: IP and expiry, about 50 bytes, so 10 million rows is about 500 MB. The reason lives in Redis. Machine count still follows the request rate.

| Piece | Load at peak | What we run |
| --- | --- | --- |
| Gateways | 100,000 RPS. About 10,000 RPS each. The extra work is a memory lookup. The box is mostly doing TLS. | **10** to carry the peak. **15** (5 per zone) so two zones still carry 100,000 if one zone is down. |
| Gateway memory | About 10,000 hot rows on the whole fleet, roughly 1 MB. | Nothing extra. |
| Redis | About **10,000 reads/s**, one per active IP per second, plus about **1,000 writes/s** when a 10-second entry is refreshed. Data for the live window is about **10 MB**. | **1 primary and 1 replica.** A third node only for a replica in another zone. One primary can do on the order of 100,000 small reads/s, so this is not a sharded cluster. |
| Reputation system | A refresh every 10 seconds: 10,000 / 10 = **about 1,000 lookups/s**. At 10–50 ms that is a few dozen calls in flight. | No new servers. This is the number to take to the owning team. The exact budget is still not agreed. |
| App servers | About 100,000 RPS of real API work, at about 1,000 RPS per instance. | About **100**, or **150** with the same zone-loss room. This follows how heavy the API is. |

The IP check is not why the gateway count is 15. TLS is.

**Current reputation load.** Coalescing and deep dive 3 replace the idea that request rate becomes lookup rate. One IP is one in-flight lookup, then silence for about 10 seconds. One-shot IPs are never asked. Distinct IPs that come back are capped at about 1,000 lookups per second, busiest first. That is the number to defend. The Redis row above, about 10,000 reads per second, belongs to the replaced deep dive 1 path where a miss read Redis on the request. The current Redis load is writes of those answers plus one small-set pull from each gateway every 2 seconds.

**If the owning team allows fewer lookups.** If they can spare only a few hundred lookups per second, a clean answer has to live longer than 10 seconds, or fewer repeat IPs are refreshed. The overflow is allowed. An empty cache does not block users while it fills.

---

## Not decided yet

Do not treat any of the following as agreed:

- The numeric reputation lookup budget the owning team will actually allow. The worked estimate is about 1,000 lookups/s under the assumptions above. That number is not an agreed cap.
- The queue or screen an operator uses to see a recorded large-share IP. The behavior is locked: that IP stays allowed until the operator blocks it, and an operator allow removes an IP from the malicious set.
- Checksum handling beyond storing the field on `ListVersion`
