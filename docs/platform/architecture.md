# NovaBank Platform — Architecture

Living document, updated at the end of every stage. Each stage adds its resources and
the reasoning behind them; nothing here is deleted as later stages extend it, except to
reflect things that were actually replaced (e.g. public access removed in Stage 7).

## Stage 1 — Foundations

```mermaid
flowchart TB
    sub([Azure Subscription<br/>Azure Subscription - Nova Bank])
    rg[Resource Group<br/>nb-dev-rg<br/>uaenorth]
    log[Log Analytics Workspace<br/>nb-dev-log<br/>PerGB2018, 30-day retention]
    budget[Budget<br/>nb-dev-budget<br/>$25/month, alerts at 50/80/100%]

    sub --> rg
    rg --> log
    rg --> budget
```

| Resource | Name | Purpose |
|---|---|---|
| Resource group | `nb-dev-rg` | Scope for every dev resource in this project; everything else gets deployed into it |
| Log Analytics workspace | `nb-dev-log` | Central log/metric sink for later stages (Container Insights in Stage 4, Application Insights in Stage 6). Empty and free until something ingests into it |
| Budget | `nb-dev-budget` | $25/month cost tripwire at the resource-group scope, emailing `m.ibbrahim45@gmail.com` at 50/80/100% of spend |

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| Subscription-scope `main.bicep` | Resource groups are themselves subscription-scoped resources; this lets one `az deployment sub create` create the RG and everything inside it in one pass | Slightly more ceremony than a resource-group-scoped template (need `scope: rg` on each module) |
| Budget scoped to the resource group, not the subscription | All project spend lives in `nb-dev-rg` by design (single dev environment); a resource-group-scoped budget tracks exactly that | If a future stage creates resources outside this RG, they won't count against the budget |
| Log Analytics PerGB2018 (pay-as-you-go) tier | Cheapest SKU that still supports Container Insights / App Insights later; no commitment tier needed at this volume | Per-GB ingestion cost instead of a flat commitment rate — fine at dev-scale log volume |
| UAE North region | Low latency to the owner; full-featured Azure region (AKS, PostgreSQL Flexible Server, APIM all available) | — |

### Naming and tagging applied
- Convention: `nb-<env>-<resource-abbreviation>`
- Tags on every resource: `project=novabank`, `env=dev`, `owner=badsha`, `managedBy=bicep`

## Stage 2 — Container registry + build pipeline

```mermaid
flowchart LR
    gh[GitHub Actions<br/>push to main]
    oidc{{token.actions.githubusercontent.com}}
    id[Managed Identity<br/>nb-dev-id-github]
    acr[(Container Registry<br/>nbdevacr&lt;suffix&gt;<br/>Basic SKU, admin disabled)]

    gh -- "OIDC token, no secret" --> oidc
    oidc -- "federated credential:<br/>repo:badshatechme-cpu/nova-bank:ref:refs/heads/main" --> id
    id -- "AcrPush only, scoped to this registry" --> acr
```

| Resource | Name | Purpose |
|---|---|---|
| Container Registry | `nbdevacr<unique>` | Stores the 3 service images; Basic SKU, admin user disabled |
| User-assigned managed identity | `nb-dev-id-github` | Stands in for GitHub Actions in Azure — no stored credential, ever |
| Federated identity credential | `github-actions-main` (on the identity) | Lets only workflow runs triggered by a push to `main` on `badshatechme-cpu/nova-bank` exchange a GitHub OIDC token for an Azure AD token |
| Role assignment | `AcrPush` → scoped to the registry | The identity can push/pull images on this one registry and nothing else — can't touch any other resource in the subscription |

**Pipeline:** `.github/workflows/build.yml` builds `customer-service`, `account-service`, and `card-service` on every push to `main`, tags each image with the commit SHA, and pushes to ACR via `az acr login` (using the OIDC-authenticated identity) + `docker push`.

**GitHub repo variables** (not secrets — safe by design since there's no credential behind them, only identifiers used to request a short-lived token): `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`, `ACR_NAME`.

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| User-assigned managed identity, not an Entra ID app registration | Managed identities (and their federated credentials) are native ARM/Bicep resources; app registrations are Microsoft Graph objects that need a separate extension or a deployment-script workaround to manage as code | Slightly less common pattern in older GitHub OIDC tutorials, which mostly show app registrations |
| Federated credential subject locked to `ref:refs/heads/main` | Only a push-triggered run on `main` can authenticate as this identity — a PR build or a run on any other branch gets no token | Need a second federated credential (or a broader subject) later if we want PR-triggered builds |
| Subject keyed on GitHub's numeric owner/repo IDs (`repo:owner@ownerId/repo@repoId:ref:...`), not names | This is the subject format GitHub's OIDC provider actually issues — found by reading the failed first run's own log output. Using stable IDs also means a future repo/org rename won't silently break the credential | Less readable in the Bicep source than the plain `owner/repo` form; the IDs have to be looked up once via the GitHub API |
| `AcrPush` scoped to the registry resource, not the resource group | Least privilege: this identity's blast radius is "can push/pull images to this one registry" | If a second registry is ever added, it needs its own role assignment |
| ACR admin user disabled | No static admin password exists to leak; every push is OIDC-authenticated | Can't `docker login` with a username/password for quick manual testing — use `az acr login` instead |
| ACR Basic SKU | Cheapest tier (~$5/month); no geo-replication or private endpoint support needed yet | Upgrade to Premium only if Stage 7 needs a private endpoint on the registry |

## Stage 3 — Database and secrets

```mermaid
flowchart TB
    subgraph psql[PostgreSQL Flexible Server nb-dev-psql]
        cdb[(customer_db)]
        adb[(account_db)]
        kdb[(card_db)]
    end

    fw[Firewall rule<br/>owner IP only]
    fw --> psql

    subgraph kv[Key Vault nbdevkv&lt;unique&gt; - RBAC mode]
        s0[postgres-admin-password]
        s1[customer-db-password]
        s2[account-db-password]
        s3[card-db-password]
    end

    idc[nb-dev-id-customer]
    ida[nb-dev-id-account]
    idk[nb-dev-id-card]

    idc -- "Key Vault Secrets User<br/>(scoped to s1 only)" --> s1
    ida -- "Key Vault Secrets User<br/>(scoped to s2 only)" --> s2
    idk -- "Key Vault Secrets User<br/>(scoped to s3 only)" --> s3
```

| Resource | Name | Purpose |
|---|---|---|
| PostgreSQL Flexible Server | `nb-dev-psql` | Burstable `Standard_B1ms`, Postgres 16, 32GB storage, no HA, 7-day backups |
| Firewall rule | `allow-owner-ip` | Public access restricted to the owner's single IP — Stage 7 removes public access entirely |
| Databases | `customer_db`, `account_db`, `card_db` | Created empty; Flyway migrates the schema when the services connect in Stage 4 |
| Key Vault | `nbdevkv<unique>` | RBAC mode (no legacy access policies) |
| Role assignment | `Key Vault Secrets Officer` → owner, scoped to the vault | Lets the owner's own deployment write the secrets below |
| Secrets | `postgres-admin-password`, `customer-db-password`, `account-db-password`, `card-db-password` | Generated at deploy time via Bicep `newGuid()`; never written to a file in the repo |
| Managed identities | `nb-dev-id-customer`, `nb-dev-id-account`, `nb-dev-id-card` | One per service, used in Stage 4 to pull each service's own DB password at runtime |
| Role assignments | `Key Vault Secrets User`, scoped to **one secret each** | `nb-dev-id-customer` can read `customer-db-password` only — not the admin password, not another service's password |

**Manual one-time step:** `scripts/create-db-roles.sh` creates the three per-service Postgres roles (`customer_app`, `account_app`, `card_app`), reading their passwords live from Key Vault. Postgres roles aren't an ARM/Bicep resource type — the only declarative alternative is an Azure deployment-script resource, which spins up a temporary container and storage account just to run a few SQL statements, and needs the firewall opened to "all Azure services" to reach the database. Running a short script yourself instead avoids that cost/complexity and keeps the live-database connection in the owner's hands, consistent with the platform/application change-control split.

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| Per-service DB passwords generated now, Postgres roles created via a manual script | Postgres users/roles have no ARM resource type; a deployment-script workaround adds cost, complexity, and a firewall exception | Two steps instead of one — Bicep deploy, then run `create-db-roles.sh` |
| Key Vault Secrets User scoped per-secret, not per-vault | Demonstrates real secret-level least privilege: a compromised service identity can only ever read its own DB password | Three near-identical role assignments instead of one vault-wide grant |
| Firewall restricted to the owner's single IP | Smallest possible exposure while the owner is the only one connecting (schema migrations, manual checks) | Breaks if the owner's IP changes (home network, VPN) — re-run the deployment with the new IP, or widen temporarily |
| Burstable `Standard_B1ms`, no HA | Cheapest SKU that still runs Postgres 16 reliably for dev/demo traffic (~$12-20/month) | No failover; acceptable for a non-production portfolio environment |
