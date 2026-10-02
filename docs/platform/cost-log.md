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

## Running cumulative total

| Stage | Expected incremental cost | Cumulative expected |
|---|---|---|
| 1 — Foundations | ~$0 | ~$0 |
| 2 — ACR + build pipeline | ~$5 | ~$5 |
