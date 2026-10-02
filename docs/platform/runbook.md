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

## Stage 2 — Container registry + build pipeline

**What it deploys:** Container Registry `nbdevacr<unique>`, managed identity
`nb-dev-id-github` with a GitHub OIDC federated credential, `AcrPush` role assignment
scoped to the registry, plus `.github/workflows/build.yml`.

### Preview and deploy

Same commands as Stage 1 (`infra/main.bicep` now includes Stage 2's modules too):

```bash
az deployment sub what-if \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam

az deployment sub create \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam \
  --name stage2-acr-pipeline
```

> **Note on `what-if` for this stage:** the `AcrPush` role assignment shows as
> "Unsupported" in the what-if diff, because its resource ID depends on the managed
> identity's principal ID, which doesn't exist until the identity is created in the same
> deployment. This is a known what-if limitation for this pattern, not an error.

### One-time setup after first deploy: GitHub repo variables

Read the identity's client ID and the registry name from the deployment output, then:

```bash
az account show --query "{subscriptionId:id, tenantId:tenantId}" -o json

gh variable set AZURE_CLIENT_ID --body "<githubIdentityClientId output>"
gh variable set AZURE_TENANT_ID --body "<tenantId>"
gh variable set AZURE_SUBSCRIPTION_ID --body "<subscriptionId>"
gh variable set ACR_NAME --body "<acrName output>"
```

These are safe as plain (non-secret) repository variables — there's no credential
behind a client ID in an OIDC federated-identity setup, only an identifier used to
request a short-lived token per workflow run.

### Verify

```bash
az acr show --name <acrName> --query "{loginServer:loginServer, adminUserEnabled:adminUserEnabled}"
az role assignment list --scope $(az acr show --name <acrName> --query id -o tsv) -o table
```

After a push to `main`:

```bash
az acr repository list --name <acrName> -o table
az acr repository show-tags --name <acrName> --repository customer-service -o table
```

**Done when:** all three repositories (`customer-service`, `account-service`,
`card-service`) exist in the registry with a tag matching the triggering commit's SHA.

### Troubleshooting

- **Workflow fails on `azure/login` with "AADSTS70021: No matching federated identity
  record found"** — the subject claim doesn't match. Check the run was triggered by a
  push to `main` (not a PR or another branch) and that the repo variables match the
  identity actually deployed.
- **`docker push` fails with 401/403** — confirm the `AcrPush` role assignment exists on
  the registry (see Verify above) and that `az acr login` ran successfully first.
