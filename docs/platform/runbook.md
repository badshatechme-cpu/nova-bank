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

- **Workflow fails on `azure/login` with "AADSTS700213: No matching federated identity
  record found", and the logged subject looks like
  `repo:owner@123456/repo@789012:ref:refs/heads/main`** — GitHub's OIDC subject claim
  now embeds the owner's and repo's stable numeric database IDs, not just their names.
  Our federated credential's `subject` must match this exact format (see
  `infra/modules/githubIdentity.bicep`'s `githubOwnerId`/`githubRepoId` params). Get the
  real IDs with `gh api repos/<owner>/<repo> --jq '{owner: .owner.id, repo: .id}'` and
  redeploy. (This bit us on the very first Stage 2 run — the workflow's own log printed
  the exact subject GitHub presented, which made the fix a one-line diff once found.)
- **`docker push` fails with 401/403** — confirm the `AcrPush` role assignment exists on
  the registry (see Verify above) and that `az acr login` ran successfully first.

## Stage 3 — Database and secrets

**What it deploys:** PostgreSQL Flexible Server `nb-dev-psql` (3 databases, firewall
locked to the owner's IP), Key Vault `nbdevkv<unique>` (RBAC mode, 4 secrets), and 3
per-service managed identities each scoped to one secret.

### Preview and deploy

Same commands as before — `infra/main.bicep` now includes Stage 3's modules too. Before
running `what-if`, refresh your current public IP and confirm it matches
`infra/env/dev.bicepparam`'s `allowedClientIp` (update and redeploy if it's changed):

```bash
curl -s https://api.ipify.org
```

```bash
az deployment sub what-if \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam

az deployment sub create \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam \
  --name stage3-database-secrets
```

### One-time setup after first deploy: create the per-service Postgres roles

```bash
./scripts/create-db-roles.sh
```

Requires the `psql` client (`brew install libpq && brew link --force libpq` on a Mac
without it already) and that you're `az login`-ed. This connects directly to the live
database using the admin password pulled fresh from Key Vault — nothing is hardcoded.

### Verify

```bash
az postgres flexible-server show --name nb-dev-psql --resource-group nb-dev-rg \
  --query "{fqdn:fullyQualifiedDomainName, state:state, sku:sku.name}"

az postgres flexible-server db list --server-name nb-dev-psql --resource-group nb-dev-rg -o table

az keyvault secret list --vault-name <keyVaultName output> --query "[].name" -o tsv

# Confirm each identity can only read its own secret:
SUB=$(az account show --query id -o tsv)
az role assignment list \
  --scope "/subscriptions/$SUB/resourceGroups/nb-dev-rg/providers/Microsoft.KeyVault/vaults/<keyVaultName>/secrets/customer-db-password" \
  -o table
```

**Done when:** all three databases exist, all four secrets are in Key Vault, and each
service identity's role assignment resolves to exactly one secret (not the vault, not
another service's secret).

### Start / stop (cost control)

PostgreSQL is billed hourly while running — stop it whenever you're done for the day:

```bash
./scripts/stop.sh    # at the end of a session
./scripts/start.sh   # before the next session
```

### Troubleshooting

- **`what-if` or deploy fails on the Key Vault secrets with "Forbidden" / insufficient
  permissions** — RBAC role assignments can take up to a minute or two to propagate.
  The owner's `Key Vault Secrets Officer` grant and the secret-write in the same
  deployment can occasionally race on a fresh vault. Simply re-run `az deployment sub
  create` — it's idempotent, and the second attempt succeeds once the role has
  propagated.
- **`create-db-roles.sh` fails to connect ("timeout expired" or "could not connect")**
  — your public IP has changed since the firewall rule was deployed. Re-check with
  `curl -s https://api.ipify.org`, update `allowedClientIp` in `dev.bicepparam`, and
  redeploy.
- **`psql: command not found`** — install the PostgreSQL client: `brew install libpq &&
  brew link --force libpq`.

## Stage 4 — AKS and first deployment

**What it deploys:** AKS cluster `nb-dev-aks`, an explicit outbound public IP wired into
the Postgres firewall, workload-identity federation for each Stage 3 identity, the
shared Helm chart, and `.github/workflows/deploy.yml`.

### Deploy the infra (same pattern as before)

```bash
az deployment sub what-if \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam

az deployment sub create \
  --location uaenorth \
  --template-file infra/main.bicep \
  --parameters infra/env/dev.bicepparam \
  --name stage4-aks-deployment
```

### One-time: get cluster credentials and create the namespace

```bash
az aks get-credentials --name nb-dev-aks --resource-group nb-dev-rg --overwrite-existing
kubectl create namespace novabank --dry-run=client -o yaml | kubectl apply -f -
```

### Deploy the services manually (the pipeline does this automatically after a build)

```bash
LATEST_SHA=$(az acr repository show-tags --name <acrName> --repository customer-service --orderby time_desc --top 1 -o tsv)

for svc in customer account card; do
  helm upgrade --install $svc-service deploy/helm/novabank-service \
    -f deploy/helm/novabank-service/values-$svc.yaml \
    --set image.tag=$LATEST_SHA \
    --namespace novabank
done
```

### Verify

```bash
kubectl get pods -n novabank
kubectl get ingress -n novabank

# Each service's own hostname (find the ingress IP via: kubectl get svc -n app-routing-system)
curl http://customer.<ingress-ip>.nip.io/actuator/health
curl http://account.<ingress-ip>.nip.io/actuator/health
curl http://card.<ingress-ip>.nip.io/actuator/health
```

**Done when:** all three pods show `1/1 Running`, and all three `/actuator/health`
endpoints return `200`.

### Seed data against the AKS deployment

**Option A — in-cluster Job (recommended, no local Docker needed):**

```bash
# 1. Build and push the data-generator image via GitHub Actions
gh workflow run build-data-generator.yml --ref main

# 2. Once it completes, get the image tag (the commit SHA it built from)
TAG=$(git rev-parse HEAD)

# 3. Run it as a one-off Job inside the cluster, using in-cluster DNS
sed "s/IMAGE_TAG/$TAG/" deploy/jobs/seed-data-job.yaml | kubectl apply -f -

# 4. Watch it run
kubectl logs -f job/seed-data -n novabank

# 5. Clean up once done
kubectl delete job seed-data -n novabank
```

**Option B — local Docker, pointed at the public ingress** (if your local Docker is
working):

```bash
docker run --rm \
  -e GENERATOR_BASEURL_CUSTOMERSERVICE=http://customer.<ingress-ip>.nip.io \
  -e GENERATOR_BASEURL_ACCOUNTSERVICE=http://account.<ingress-ip>.nip.io \
  -e GENERATOR_BASEURL_CARDSERVICE=http://card.<ingress-ip>.nip.io \
  novabank-data-generator
```

(Build the image locally first with `docker compose build data-generator` if you haven't.)

### Start / stop (cost control)

`scripts/start.sh` / `scripts/stop.sh` now also start/stop the AKS cluster alongside
PostgreSQL — both bill hourly while running.

### Troubleshooting

- **`BadRequest: The VM size of <X> is not allowed in your subscription in location
  <region>`** — fresh subscriptions often start with zero quota for entire VM families
  (we hit this for the whole Burstable family). The error message lists every size that
  *is* allowed — pick a small one from that list rather than filing a quota-increase
  request if you're not in a hurry.
- **`MissingSubscriptionRegistration` for `Microsoft.OperationsManagement` or
  `Microsoft.Insights`** — register the provider once per subscription, then redeploy:
  `az provider register --namespace Microsoft.OperationsManagement` (and `.Insights`).
  Wait for `az provider show --namespace <ns> --query registrationState` to say
  `Registered` before retrying.
- **App pods stuck `Pending` with `Insufficient cpu`, but the Azure Portal shows the
  node's CPU usage as low (e.g. 22%)** — these are different numbers. The portal shows
  *actual usage*; the Kubernetes scheduler only looks at *requests* (reservations). Check
  real headroom with `kubectl describe node | grep -A6 "Allocated resources"` and
  `kubectl describe node | grep -A6 "^Allocatable:"`. If system add-ons alone consume
  most of the requests, lower `resources.requests.cpu` in
  `deploy/helm/novabank-service/values.yaml`, or scale the node pool up.
- **Pods crash-loop with `password authentication failed`, and it previously worked** —
  almost certainly a stale CSI-synced Kubernetes Secret (`<service>-db-secret`). Since
  `enableSecretRotation` is `false`, that Secret is created once and never refreshed —
  restarting the *pod* doesn't touch it. Delete it directly and restart the pod:
  ```bash
  kubectl delete secret <service>-service-db-secret -n novabank
  kubectl delete pod -n novabank -l app=<service>-service
  ```
- **Pods crash-loop on DB auth right after an *unrelated* infra redeploy** — check
  whether `infra/main.bicep`'s password parameters still default to `newGuid()`
  (they shouldn't — see `architecture.md`'s Stage 4 notes). `newGuid()` regenerates on
  every `az deployment sub create`, silently rotating Key Vault's secrets out from under
  the actual Postgres role passwords. If you ever see this, re-run
  `./scripts/create-db-roles.sh` to resync, then delete the stale CSI secrets as above.
- **Pods can't reach Postgres (`Connect timed out`), but the owner's IP firewall rule is
  correct** — pods connect out through **AKS's outbound IP**, not the owner's IP. Confirm
  `infra/modules/postgres.bicep` has the `allow-aks-outbound-ip` firewall rule and that
  it matches the cluster's actual outbound IP (`az aks show --name nb-dev-aks
  --resource-group nb-dev-rg --query networkProfile.loadBalancerProfile`).
