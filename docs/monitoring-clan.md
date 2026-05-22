# Clan Monitoring

## Scope

This monitoring change instruments `service-clan` with custom Micrometer
metrics in addition to the default Spring Boot Actuator metrics already exposed
at `/actuator/prometheus`.

## Design Justification

The service uses Spring Actuator, Micrometer, Prometheus, and Grafana because the
Yomu stack already provisions Prometheus scraping and Grafana dashboards for
service observability. This keeps the monitoring path consistent with the rest
of the microservices and avoids adding another runtime dependency.

Custom metrics are intentionally low-cardinality. Tags use bounded values such
as action name, outcome, event type, and tier-change source. They do not include
`userId`, `clanId`, `memberId`, or achievement codes, because those values would
create unbounded Prometheus series and make the monitoring system harder to
operate.

Counters are used for traffic, event volume, and tier changes. Timers are used
for latency on business actions. Activity event counters distinguish `processed`,
`skipped_no_member`, and `duplicate` outcomes so idempotent consumer behavior
can be observed.

## Metrics

| Metric | Type | Labels | Purpose |
| :-- | :-- | :-- | :-- |
| `yomu_clan_actions_total` | Counter | `action`, `outcome` | Counts service-level actions and failures. |
| `yomu_clan_action_duration_seconds` | Timer histogram | `action`, `outcome` | Tracks action latency and supports percentile queries. |
| `yomu_clan_activity_events_total` | Counter | `event`, `outcome` | Tracks consumed activity events and duplicate suppression. |
| `yomu_clan_tier_changes_total` | Counter | `direction`, `source` | Counts clan tier promotions and demotions. |

### Activity event labels

| `event` | Meaning |
| :-- | :-- |
| `quiz_completed` | Quiz activity processed for an accepted member |
| `achievement_unlocked` | Achievement bonus applied or skipped |
| `mission_completed` | Daily mission completion recorded |
| `mission_reward_claimed` | Mission reward points applied |

| `outcome` | Meaning |
| :-- | :-- |
| `processed` | Event applied to clan score |
| `skipped_no_member` | User is not an accepted clan member |
| `duplicate` | Achievement bonus already processed (idempotent skip) |

### Tier change labels

| `direction` | `source` |
| :-- | :-- |
| `promoted` / `demoted` | `auto_score` (runtime tier from member scores) or `season_end` (admin end-of-season) |

## Example Usage

Check whether Prometheus can scrape the service:

```bash
curl http://<STAGING_HOST>:8085/actuator/prometheus
```

Via API Gateway:

```bash
curl http://<STAGING_HOST>:8090/api/clan/leaderboard
```

PromQL examples:

```promql
rate(yomu_clan_actions_total{outcome="failure"}[5m])
```

```promql
histogram_quantile(
  0.95,
  sum(rate(yomu_clan_action_duration_seconds_bucket[5m])) by (le, action)
)
```

```promql
increase(yomu_clan_activity_events_total{outcome="duplicate"}[1h])
```

```promql
increase(yomu_clan_tier_changes_total[1h])
```

```promql
rate(yomu_clan_actions_total{action="get_leaderboard"}[5m])
```

## Expected Operational Signals

- A spike in `yomu_clan_actions_total{outcome="failure"}` indicates broken API
  usage, invalid state transitions, or persistence issues on join/create paths.
- A sustained rise in duplicate achievement events means RabbitMQ redelivery or
  upstream duplicate publication is happening, but idempotency is still working.
- A drop in `processed` activity events while HTTP traffic remains normal can
  indicate listener or membership lookup problems.
- High p95 latency on `get_leaderboard` or `trigger_end_of_season` points to
  repository query patterns that should be profiled further.
- Unexpected spikes in `yomu_clan_tier_changes_total` may indicate scoring bugs
  or repeated season-end runs.
