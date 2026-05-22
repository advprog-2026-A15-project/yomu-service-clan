# Clan Profiling Evidence

## Profiling Run

Date: May 22, 2026

Target: `service-clan` running locally on port `18085` from the `bootJar` artifact,
with a local H2 database and security bypass enabled (`yomu.security.bypass=true`).

The workload exercised the main user-facing read paths, membership writes, and
admin season processing:

- `POST /api/clan` (5 clans created)
- `GET /api/clan/leaderboard` (201 requests)
- `GET /api/clan/{id}` (50 requests)
- `POST /api/clan/{clanId}/join` (20 requests, mostly expected conflicts)
- `POST /api/clan/admin/end-season` (1 request, failure on small dataset)

Reproduce locally:

```bash
./scripts/run-profiling.sh
```

## Evidence Files

- Raw JFR recording: [`profiling/clan-runtime.jfr`](profiling/clan-runtime.jfr)
- JFR event summary: [`profiling/jfr-summary.txt`](profiling/jfr-summary.txt)
- Hot methods view: [`profiling/jfr-hot-methods.txt`](profiling/jfr-hot-methods.txt)
- Allocation by class: [`profiling/jfr-allocation-by-class.txt`](profiling/jfr-allocation-by-class.txt)
- Prometheus snapshot: [`profiling/prometheus-metrics-snapshot.txt`](profiling/prometheus-metrics-snapshot.txt)
- JFR dump command output: [`profiling/jfr-dump-command.txt`](profiling/jfr-dump-command.txt)
- JFR stop command output: [`profiling/jfr-stop-command.txt`](profiling/jfr-stop-command.txt)

## Process Justification

Java Flight Recorder was used because it profiles JVM CPU samples, allocation
pressure, GC, and runtime events with low overhead. That makes it appropriate for
a Spring Boot service where the relevant risks are CPU hotspots, allocation
pressure, database access, request latency, and GC behavior.

Prometheus metrics were captured from `/actuator/prometheus` during the same run
because they show service-level latency, throughput, and error counters from the
same custom `yomu_clan_*` instrumentation used in staging. JFR explains where time
and allocation go inside the JVM; Prometheus explains how the service behaves from
the runtime API surface.

## Observed Results

Prometheus captured the following custom metric counts:

- `create_clan`: 5 successful requests.
- `get_leaderboard`: 201 successful requests.
- `get_clan_by_id`: 50 successful requests.
- `join_clan`: 1 success and 19 expected failures (duplicate join attempts).
- `trigger_end_of_season`: 1 failure on a small clan dataset.
- `recalculate_all_tiers`: 1 success triggered as part of season processing.

Average local action latency from the Prometheus snapshot:

| Action | Count | Sum seconds | Approx average |
| :-- | --: | --: | --: |
| `create_clan` success | 5 | 0.013552 | 2.71 ms |
| `get_leaderboard` success | 201 | 0.081886 | 0.41 ms |
| `get_clan_by_id` success | 50 | 0.004919 | 0.10 ms |
| `join_clan` failure path | 19 | 0.007945 | 0.42 ms |
| `join_clan` success path | 1 | 0.000695 | 0.70 ms |
| `recalculate_all_tiers` success | 1 | 0.021935 | 21.94 ms |
| `trigger_end_of_season` failure | 1 | 0.028752 | 28.75 ms |

JFR hot methods during this short run were dominated by Tomcat request handling,
H2 JDBC statement cleanup, and Spring annotation metadata work. Allocation samples
included `byte[]`, `String`, and JDBC-related objects. The recording also includes
service startup, so a longer steady-state run would surface more business-method
hotspots.

## Analysis And Improvements

The local read endpoints are fast with the small H2 profiling dataset, but this
does not prove production scalability. The important risks are in paths that
scale with clan count, member count, and event volume.

Recommended improvements:

1. Add database indexes for `clan_members(clan_id)`, `clan_members(user_id)`, and
   `clans(tier)` to speed leaderboard and membership lookups.
2. Reduce N+1 query behavior in `getLeaderboard` without a tier filter by batching
   member loads instead of querying members per clan in a loop.
3. Optimize `updateClanStatus` by reducing round trips when quiz/mission events
   arrive frequently; consider a materialized clan score updated incrementally.
4. Make `triggerEndOfSeason` safer for large deployments with background processing
   and batched RabbitMQ publishing instead of per-member sends inside one
   transaction.
5. Move RabbitMQ publish for league activity and tier events to after-commit or
   an outbox pattern so broker latency does not extend DB transactions.
6. Split future profiling into startup profiling and steady-state load profiling.
   This run includes service startup; a longer steady-state run would expose
   `ClanServiceImpl` and repository hotspots more clearly.
