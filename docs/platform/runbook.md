# NovaBank Platform — Runbook

How to deploy, check, and tear down each stage. Updated as new stages add their own
start/stop/deploy/rollback steps.

## Prerequisites (Stage 0)

```bash
az login
az account show   # confirm it shows "Azure Subscription - Nova Bank"
```

## Stage 1 — Foundations

**What it deploys:** resource group `nb-dev-rg`, Log Analytics workspace `nb-dev-log`,
budget `nb-dev-budget` ($25/month, alerts at 50/80/100%).

### Preview changes (always run before deploying)

```bash
az deployment sub what-if \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam
```

### Deploy

```bash
az deployment sub create \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam \
  --name stage1-foundations
```

### Verify

```bash
az group show --name nb-dev-rg
az monitor log-analytics workspace show --resource-group nb-dev-rg --workspace-name nb-dev-log
az consumption budget show --resource-group nb-dev-rg --budget-name nb-dev-budget
```

### Rollback

Stage 1 has no application state, so rollback is deleting the resource group (this
also removes everything later stages add inside it — only do this if you mean to start
over):

```bash
az group delete --name nb-dev-rg --yes --no-wait
```

### Troubleshooting

- **`az login` fails with "No space left on device"** — the Mac's disk is full; Azure
  CLI can't write its token cache lock file. Free space (`docker builder prune -f`,
  clear `~/Library/Caches`) and retry.
- **`az login` succeeds but shows "No subscriptions found"** — the signed-in account has
  no active Azure subscription in that tenant. Check portal.azure.com's subscription
  status (free trial can be stuck in "under review" for a day or two), or `az login`
  again and pick a different account at the browser prompt.
- **Deployment takes a couple of minutes** — subscription-scope deployments that create
  a new resource group typically take 1-2 minutes; this is normal.
