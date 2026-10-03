# NovaBank Platform — Cost Log

Expected vs. observed cost per stage. Raw material for the later FinOps review project —
record what we actually see billed, not just the estimate.

## Budget

A $25/month budget (`nb-dev-budget`) was created in Stage 1, scoped to `nb-dev-rg`, with
email alerts at 50% ($12.50), 80% ($20), and 100% ($25) to `m.ibbrahim45@gmail.com`.

## Stage 1 — Foundations

| Resource | SKU | Expected monthly cost | Notes |
|---|---|---|---|
| Resource group `nb-dev-rg` | — | $0 | Resource groups are free; they're just a management boundary |
| Log Analytics workspace `nb-dev-log` | PerGB2018 (pay-as-you-go) | ~$0 | No charge until something ingests logs; billed per GB ingested + per-GB/month retained beyond the free 31-day included amount once data starts flowing (Stage 4/6) |
| Budget `nb-dev-budget` | — | $0 | Budgets themselves are free |
| **Stage 1 total** | | **~$0/month** | |

**Observed:** _(check the Cost Management view for `nb-dev-rg` after a few days and fill
in actual spend here)_

## Stage 2 — Container registry + build pipeline

| Resource | SKU | Expected monthly cost | Notes |
|---|---|---|---|
| Container Registry `nbdevacr<unique>` | Basic | ~$5/month | Flat fee for the registry; image storage beyond the included 10GB adds a small per-GB charge |
| Managed identity `nb-dev-id-github` | — | $0 | Managed identities are free |
| Federated identity credential | — | $0 | Free |
| Role assignment (`AcrPush`) | — | $0 | Free |
| GitHub Actions minutes | — | $0 | Repo is public — unlimited free minutes |
| **Stage 2 total** | | **~$5/month** | |

**Observed:** _(fill in after a billing cycle)_

## Stage 3 — Database and secrets

| Resource | SKU | Expected monthly cost | Notes |
|---|---|---|---|
| PostgreSQL Flexible Server `nb-dev-psql` | Burstable `Standard_B1ms`, 32GB storage | ~$12-20/month | Billed hourly while running — use `scripts/stop.sh`/`start.sh` between sessions to cut this significantly |
| Key Vault `nbdevkv<unique>` | Standard | ~$0 | Pay-per-operation; negligible at this volume |
| Managed identities (×3) | — | $0 | Free |
| Role assignments (×4) | — | $0 | Free |
| **Stage 3 total (running continuously)** | | **~$12-20/month** | Closer to $0 if stopped outside active work sessions |

**Observed:** _(fill in after a billing cycle)_

## Stage 4 — AKS and first deployment

| Resource | SKU | Expected monthly cost | Notes |
|---|---|---|---|
| AKS cluster `nb-dev-aks` | Free tier control plane | $0 | Free tier has no SLA but no charge |
| Node pool | 1x `Standard_D2als_v6` | ~$72/month if run continuously | **Revised up from the ~$30/month Burstable estimate** — `Standard_B2s` wasn't permitted on this subscription (zero quota for the whole Burstable family on a fresh subscription); this was the smallest allowed general-purpose size. Billed hourly — `scripts/stop.sh`/`start.sh` matter a lot here |
| Outbound public IP `nb-dev-aks-outbound-ip` | Standard SKU | ~$3-4/month | Needed so Postgres's firewall can allow a known, stable address for pod traffic |
| Federated credentials (×3) | — | $0 | Free |
| Role assignments (×2) | — | $0 | Free |
| Container Insights / Log Analytics ingestion | Pay-as-you-go, 1GB/day hard cap | ~$0-5/month | Observed ~0.05GB/day in practice — well under the cap. Daily quota added after a cost audit found ingestion was uncapped |
| Ingress controller's public IP | Standard SKU, $0.005/hr | ~$3.65/month | **Found during a post-deployment cost audit** — missed in the original estimate. Separate from the explicit outbound IP; this one is auto-created by the App Routing add-on's `LoadBalancer` Service |
| AKS's Standard Load Balancer | Base rate | ~$18/month | **Also found during the audit.** Required by AKS itself — handles both outbound SNAT for the cluster and inbound routing for the ingress. Not something we chose; comes with running AKS with a public ingress at all |
| **Stage 4 total (running continuously)** | | **~$99-113/month** | Revised up from the original ~$77-86 estimate after the audit. Much lower in practice if the cluster is stopped outside active work sessions — the two items above don't stop billing when you `stop.sh` (they're tied to the cluster's existence, not its power state), but the node and Postgres do |

**Observed:** _(fill in after a billing cycle)_

**Audit note (post-Stage 4):** every SKU here was re-checked against Azure's live retail
pricing and the subscription's actual allowed-SKU list — Postgres (`Standard_B1ms`) and
the AKS node (`Standard_D2als_v6`) are both already the cheapest valid options; no
cheaper tier exists for Log Analytics, ACR, or Key Vault either. The one real
miss was the two line items above, now added. Stage 1's $25/month budget alert will fire
once billing data catches up — it's correctly flagging that actual continuous-run cost
now exceeds that original threshold, not a false alarm.

## Stage 5 — API Management and Entra ID security

| Resource | SKU | Expected monthly cost | Notes |
|---|---|---|---|
| APIM `nb-dev-apim` | Consumption tier | ~$0 | True pay-per-call: first 1M calls/month free, then ~$3.50/million. Chosen specifically to minimize cost — explicitly asked for and confirmed before provisioning |
| Entra ID app registration, scopes, app role, test users | — | $0 | Entra ID (Microsoft Graph), not a billed Azure resource |
| **Stage 5 total** | | **~$0/month** | The only Azure resource this stage adds is APIM, and at portfolio-demo call volumes it stays within the free tier entirely |

**Observed:** _(fill in after a billing cycle)_

## Running cumulative total

| Stage | Expected incremental cost | Cumulative expected |
|---|---|---|
| 1 — Foundations | ~$0 | ~$0 |
| 2 — ACR + build pipeline | ~$5 | ~$5 |
| 3 — Database and secrets | ~$12-20 (if left running) | ~$17-25 |
| 4 — AKS and first deployment | ~$99-113 (if left running) | ~$116-138 |
| 5 — APIM and Entra ID security | ~$0 | ~$116-138 |
