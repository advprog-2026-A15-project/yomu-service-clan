# Staging Deployment Links

The team's staging host is `yomu-infra.duckdns.org`. Replace `<STAGING_HOST>`
with that host or your EC2 public DNS/IP when submitting these links.

## Service Links

| Service | Staging health link | Main runtime link |
| :-- | :-- | :-- |
| API Gateway | `http://<STAGING_HOST>:8090/actuator/health` | `http://<STAGING_HOST>:8090` |
| Auth Service | `http://<STAGING_HOST>:8081/actuator/health` | `http://<STAGING_HOST>:8081` |
| Learning Service | `http://<STAGING_HOST>:8082/actuator/health` | `http://<STAGING_HOST>:8082` |
| Achievements Service | `http://<STAGING_HOST>:8083/actuator/health` | `http://<STAGING_HOST>:8083/api/achievements` |
| Forum Service | `http://<STAGING_HOST>:8084/actuator/health` | `http://<STAGING_HOST>:8084` |
| Clan Service | `http://<STAGING_HOST>:8085/actuator/health` | `http://<STAGING_HOST>:8085/api/clan` |
| Notification Service | `http://<STAGING_HOST>:8086/actuator/health` | `http://<STAGING_HOST>:8086` |
| Prometheus | `http://<STAGING_HOST>:9090/-/healthy` | `http://<STAGING_HOST>:9090/targets` |
| Grafana | `http://<STAGING_HOST>:3000` | `http://<STAGING_HOST>:3000/dashboards` |

## Clan Links To Submit

- Health: `http://yomu-infra.duckdns.org:8085/actuator/health`
- Prometheus metrics: `http://yomu-infra.duckdns.org:8085/actuator/prometheus`
- Gateway route: `http://yomu-infra.duckdns.org:8090/api/clan/leaderboard`
- Prometheus target: `http://yomu-infra.duckdns.org:9090/targets?search=service-clan`

## Deployment Source

The Clan service is deployed through the production compose image:

```text
ghcr.io/advprog-2026-a15-project/service-clan:latest
```

The infra compose exposes `service-clan` on port `8085` and the Prometheus scrape
job targets `service-clan:8085/actuator/prometheus`.
