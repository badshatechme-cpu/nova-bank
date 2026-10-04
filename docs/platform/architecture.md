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
| Secrets | `postgres-admin-password`, `customer-db-password`, `account-db-password`, `card-db-password` | Deterministic per-environment value (see Stage 4's note on why this isn't `newGuid()`); never written to a file in the repo |
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

## Stage 4 — AKS and first deployment

```mermaid
flowchart TB
    internet((Internet))
    lb[Ingress Controller<br/>App Routing add-on<br/>one hostname per service via nip.io]

    subgraph aks[AKS nb-dev-aks - 1x Standard_D2als_v6]
        cs[customer-service pod]
        as[account-service pod]
        ks[card-service pod]
    end

    csi[Secrets Store CSI driver]
    kv[(Key Vault)]
    psql[(PostgreSQL<br/>via explicit outbound IP)]

    internet --> lb
    lb --> cs
    lb --> as
    lb --> ks
    cs & as & ks -- "workload identity, CSI mount" --> csi
    csi --> kv
    cs & as & ks -- "SPRING_DATASOURCE_*" --> psql
```

| Resource | Name | Purpose |
|---|---|---|
| AKS cluster | `nb-dev-aks` | Free tier, 1x `Standard_D2als_v6` system node, OIDC issuer + workload identity, Secrets Store CSI driver, App Routing (managed NGINX ingress), Container Insights → `nb-dev-log` |
| Outbound public IP | `nb-dev-aks-outbound-ip` | Explicitly provisioned (not AKS's auto-managed one) so Bicep can feed its address straight into the Postgres firewall |
| Federated credentials | `aks-workload-identity` on each Stage 3 identity | Subject `system:serviceaccount:novabank:<service>-service`, issuer = the cluster's own OIDC issuer — pods authenticate to Key Vault with zero stored credentials |
| Role assignments | `AcrPull` → AKS kubelet identity (scoped to the registry); `Azure Kubernetes Service Cluster User Role` → the GitHub pipeline identity (scoped to the cluster) | Nodes can pull images; the pipeline can fetch a kubeconfig and deploy — nothing more |
| Helm chart | `deploy/helm/novabank-service/` | One shared chart, three values files; Deployment (liveness/readiness on Actuator, low resource requests), ServiceAccount (workload identity annotated), SecretProviderClass (Key Vault → mounted file + native K8s Secret), Service, Ingress |
| Pipeline | `.github/workflows/deploy.yml` | Triggered by `workflow_run` once `build.yml` succeeds on `main`; same OIDC identity, `helm upgrade --install` per service with the built commit SHA as the image tag |

### What actually went wrong, and the fixes (all real, all worth keeping)

| Problem | Root cause | Fix |
|---|---|---|
| `Standard_B2s` rejected at deploy | Fresh pay-as-you-go subscriptions start with **zero Burstable-family quota** in most regions | Switched to `Standard_D2als_v6` (general-purpose, in the subscription's allowed-SKU list) — costs more (~$72/mo vs ~$30/mo if left running), mitigated by `stop.sh`/`start.sh` |
| AKS deployment failed: `MissingSubscriptionRegistration` for `Microsoft.OperationsManagement` / `Microsoft.Insights` | Fresh subscriptions don't pre-register every resource provider; Container Insights needs both | `az provider register --namespace <ns>` once, then redeploy — one-time per subscription |
| 3 app pods stuck `Pending`: `Insufficient cpu` | A single small node's **CPU requests** (not actual usage) were ~95-98% consumed by AKS's own add-ons (ingress, Container Insights, CSI driver, workload identity webhook) before any app pod was scheduled | Lowered the chart's default CPU request from 100m → 20m. The Azure Portal's node CPU% shows *actual usage* (was only ~22%); the scheduler only looks at *requests* — these are different numbers and both matter for different reasons |
| Pods crash-looped: `password authentication failed` | Postgres's firewall only allowed the **owner's own IP** (Stage 3) — pods reach Postgres through **AKS's outbound IP**, which isn't the same address | Explicitly provisioned an outbound public IP in Bicep (`modules/aks.bicep`) and added a second Postgres firewall rule for it, so this stays correct even if the cluster is rebuilt |
| Pods crash-looped again after a later fix: same password error, different password | `postgresAdminPassword`/`*DbPassword` defaulted to `newGuid()`, which **re-evaluates on every single `az deployment sub create` run** — not just the first. Three unrelated redeploys (VM size, provider registration, outbound IP) each silently rotated all 4 Key Vault secrets, desyncing them from whatever `create-db-roles.sh` had actually set on the live Postgres roles | Switched the 4 password parameters to a **deterministic** `uniqueString()`-based expression (seeded by subscription ID + a fixed per-secret label) — same value every redeploy, forever, while still never appearing as a literal in the repo |
| Pods still crash-looped immediately after the deterministic-password fix + re-running `create-db-roles.sh` | The Secrets Store CSI driver's `secretObjects` sync creates a **native Kubernetes Secret once**; with `enableSecretRotation: 'false'`, that Secret object is never refreshed by anything short of deleting it — restarting the *pod* alone doesn't touch the already-existing Secret | Deleted the 3 stale `<service>-db-secret` objects directly so the CSI driver recreated them from Key Vault's current value on the next mount |

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| General-purpose `Standard_D2als_v6`, not Burstable | The only path that actually works on this subscription without a support-ticket wait for quota | ~2.4x the cost of the original estimate if left running continuously |
| One shared outbound public IP, explicit in Bicep | Keeps "which IP can reach Postgres" fully declared and reproducible, instead of a value you'd have to look up and hand-enter after every cluster rebuild | One more resource (~$3-4/month) |
| Deterministic secrets instead of `newGuid()` | `newGuid()` is the textbook Bicep pattern for *first-time* secret generation, but nothing in that pattern stops it from firing again on every later redeploy — we hit that the hard way | Secure-parameter-default linter warning (expected and accepted — the whole point is that these are *not* fresh randomness each run) |
| CSI secret rotation left disabled | Keeps the cluster simpler and avoids an extra reconciliation loop running constantly for a 3-pod dev cluster | Any future password change requires manually deleting the synced Secret (documented in the runbook) rather than it refreshing on its own |
| `nip.io` hostnames instead of path-based ingress routing | The account/card APIs nest under `/api/v1/customers/{id}/...`, the same prefix customer-service itself owns — simple path routing would collide between services | Not a real custom domain; Stage 5's APIM replaces this with proper OpenAPI-aware routing |

## Stage 5 — API Management and Entra ID security

```mermaid
flowchart LR
    client((Client))
    apim[APIM Consumption tier<br/>validate-jwt: issuer + audience<br/>per-API: scope check]
    entra[(Entra ID)]
    cs[customer-service]
    as[account-service]
    ks[card-service]

    client -- "Bearer token" --> apim
    client -. "ROPC token request" .-> entra
    apim -- "customer/*" --> cs
    apim -- "account/*" --> as
    apim -- "card/*" --> ks
    cs & as & ks -- "validates JWT again<br/>+ ownership check" --> entra
```

| Resource | Name | Purpose |
|---|---|---|
| Entra ID app registration | `NovaBank API` | Exposes 4 delegated scopes (`accounts.read`, `cards.read`, `cards.write`, `transfers.write`) and app role `NovaBank.Staff`; configured as its own public test client for ROPC |
| Directory extension attribute | `extension_<appid>_customerId` (configured name) | Maps each test user to a real seeded customer; emitted in access tokens — see the claim-name gotcha below |
| APIM | `nb-dev-apim`, Consumption tier | 3 APIs (one per service, imported from each service's live OpenAPI spec), one product (`novabank`) grouping all 3, `validate-jwt` at product level (issuer + audience) and per-API (scope, by HTTP method) |
| Services | all 3 | Added `spring-boot-starter-oauth2-resource-server`: validates the JWT a second time (issuer + audience, defence in depth), then an `OwnershipCheckFilter` compares the token's customerId claim to the path, 404 on mismatch, `NovaBank.Staff` role bypasses it |

**APIM routing:** each service's OpenAPI spec is imported as its own API with a distinct path prefix (`/customer`, `/account`, `/card`) rather than reusing the `nip.io`-per-hostname trick from Stage 4. APIM matches by the API's *own* path namespace plus each operation's full route template, so the account/card services nesting under `/api/v1/customers/{id}/...` — the same prefix customer-service itself owns — doesn't collide the way it would with simple ingress path-prefix routing.

**Verified live, all four scenarios, through the real APIM gateway with real Entra ID tokens:**
own data → `200` with real account data; another customer's data → `404`; a token missing
`transfers.write` → `403`; no token → `401`. The second ("missing scope") scenario needed a
separate, minimal test-client app registration — see the gotchas table below for why.

### What actually went wrong, and the fixes (all real, all worth keeping)

| Problem | Root cause | Fix |
|---|---|---|
| APIM deployment failed: `'POST' is an unexpected token` | A C# policy expression's `condition="@(context.Request.Method == "POST")"` has unescaped double quotes colliding with the XML attribute's own delimiter — invalid XML despite matching Microsoft's own documented examples verbatim | Escaped as `&quot;POST&quot;`. Later, a `GetValueOrDefault<Jwt>(...)` generic also needed `&lt;`/`&gt;` escaping for the same reason |
| APIM deployment failed: two APIs can't share the same empty path | All 3 services' OpenAPI specs were imported with `path: ''`, and APIM requires a unique (path, protocol, type) tuple per API regardless of whether the underlying operations would actually disambiguate | Gave each API a distinct path (`customer`/`account`/`card`) — APIM strips its own path prefix before forwarding to the backend, so this doesn't affect the services at all |
| First API calls through APIM returned `404 Resource not found` on some APIs but not others | Not a bug — newly-*created* APIM resources (the ones that failed and were recreated) take a few minutes to propagate to the Consumption tier's actual gateway runtime; an API that already existed from an earlier partial deploy (just updated) was unaffected | Waited ~5 minutes; confirmed via a request to a *different* operation on the same API that didn't depend on the lagging config |
| Every request returned `401` even with a freshly-issued, valid token | APIM's `apiAudience` was configured as the Application ID URI (`api://<appid>`), but Entra ID issues the `aud` claim as the **raw GUID** for a self-referential app (one acting as both the API and its own test client) — the URI form only applies when client and resource are separate apps | Changed the configured audience to the raw app ID everywhere (APIM policy, all 3 services' `expected-audience`, test token helpers) |
| Scope-restricted token still had all 4 scopes; "missing scope" test couldn't produce a real 403 | ROPC against this self-referential app returns the full admin-consented scope grant regardless of the narrower `scope` parameter requested — expected Entra ID behaviour for this app shape, not a bug | Created a second, minimal "test client" app registration granted only `accounts.read`, used for the one scenario that specifically needs a token missing a scope |
| `<validate-jwt required-claims>` never matched a specific scope even though the token genuinely had it | Entra ID's `scp` claim is **one space-delimited string** (`"accounts.read cards.read ..."`), not a JSON array — `required-claims` does an exact match against the whole claim value, which a single scope name can never equal | Replaced with an expression-based check: `output-token-variable-name="jwt"` on the product-level `validate-jwt`, then `jwt.Claims.GetValueOrDefault("scp", new string[0]).Any(s => s.Split(' ').Contains(required))` at the API level |
| The `customerId` claim never appeared in issued tokens, for what looked like 30+ minutes | Two independent bugs, not propagation delay: (1) `optionalClaims` for a directory extension attribute needs `"source": "user"` explicitly — `source: null` (correct for built-in claims) silently does nothing for extension attributes; (2) once fixed, the claim appears under the **abbreviated name `extn.customerId`**, not the full `extension_<appid>_customerId` form used everywhere in Graph API configuration — and as a single-element **array**, not a plain string | Fixed `source`, then changed every service's `customer-id-claim` config to `extn.customerId` and updated `OwnershipCheckFilter` to read it as a list and take the first element |

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| APIM Consumption tier | True pay-per-call pricing (~$0 at portfolio-demo volume, first 1M calls/month free) — explicitly chosen over Developer (~$50/month flat) or Standard v2 (~$73/month flat) given the stated goal of minimizing cost | No VNet integration at all; Stage 7's private-networking goals will need either a tier swap or accepting APIM outside the VNet |
| One APIM product grouping all 3 service APIs | Matches the build plan's "one API product" framing and keeps a single place to apply the base JWT check and rate limit | Per-service API-level policies still needed for per-operation scope requirements — the product-level policy alone can't distinguish between services |
| Scope required by HTTP method, not by operation | Each service's write operations all happen to be `POST` and its reads `GET` — a method-based rule covers every current operation without needing to know APIM's auto-generated per-operation resource names (unavailable until after the OpenAPI import completes) | Not fully general — a future `PATCH`/`PUT` operation would need the policy extended, not just the OpenAPI spec |
| 404 (not 403) on ownership mismatch | Explicit requirement in `CLAUDE-PLATFORM.md`: don't let the status code confirm a resource exists for a customer that isn't the caller | A genuinely malformed/missing resource and someone else's resource are indistinguishable to the caller — intentional |
| `NovaBank.Staff` role checked before the ownership comparison, not instead of real auth | Staff tokens still go through full JWT validation (issuer, audience, signature) — the bypass only skips the *customerId-vs-path* check, nothing else | None meaningful — this is the intended behaviour |

## Stage 6 — Observability

```mermaid
flowchart LR
    apim[APIM] --> cs[customer-service]
    apim --> as[account-service]
    apim --> ks[card-service]
    cs & as & ks -- "App Insights Java agent<br/>auto-instruments HTTP + JDBC" --> ai[(Application Insights<br/>workspace-based)]
    pods[AKS pods] -- "Container Insights<br/>since Stage 4" --> law[(nb-dev-log)]
    ai -. "same workspace" .-> law
    law --> wb[Observability workbook]
    ai --> alert1[5xx rate alert]
    law --> alert2[Pod crash-loop alert]
```

| Resource | Name | Purpose |
|---|---|---|
| Application Insights | `nb-dev-appinsights` | Workspace-based (not classic) — ingests into `nb-dev-log`, sharing Stage 4's 1GB/day cap rather than adding a second, uncapped data store |
| Java agent | v3.7.10, all 3 services | Attached via `-javaagent:` in each Dockerfile; configured purely by `APPLICATIONINSIGHTS_CONNECTION_STRING` (from Key Vault via CSI, same pattern as DB passwords) and `APPLICATIONINSIGHTS_ROLE_NAME` (per-service, so traces attribute correctly) — no connection string or config file in code |
| Workbook | `NovaBank Platform Observability` | Request rate, error rate, latency (avg/P95) per service from `requests`, plus pod restarts from Container Insights' `KubePodInventory` — one workbook spanning both data sources since they share a workspace |
| Alerts | `nb-dev-5xx-rate-alert`, `nb-dev-pod-crashloop-alert` | Scheduled query rules (not classic metric alerts) — 5xx count > 5 in 5 minutes; any `BackOff` event in the `novabank` namespace in 5 minutes. Both notify `nb-dev-alerts` (email) |

**Why the Java agent needs no code changes for the Postgres leg of a trace:** it auto-instruments JDBC at the bytecode level, so every `SPRING_DATASOURCE_URL` call the services already make shows up as a SQL dependency in the trace automatically.

### Decisions
| Decision | Why | Trade-off |
|---|---|---|
| Workspace-based Application Insights, not classic | Shares Stage 4's Log Analytics workspace and its 1GB/day ingestion cap — one place to control observability cost, not two | Slightly different query experience than classic App Insights' own dedicated store (irrelevant here since we query via the workspace either way) |
| Java agent downloaded in the Dockerfile, not baked into a shared base image | Keeps each service's Dockerfile self-contained and independently buildable, matching this project's per-service deployment model | Same JAR downloaded 3 times across 3 image builds instead of once — a few extra seconds of build time, not worth a shared base image for 3 services |
| `APPLICATIONINSIGHTS_ROLE_NAME` set per-service via Helm values (not auto-detected) | Without it, all 3 services would show up under one generic cloud role name in Application Insights, making the Application Map and per-service queries far less useful | None — this is pure upside for a one-line value |
| `startupProbe` (up to 300s) gates liveness; probe timeouts 5s; no fixed `initialDelaySeconds` | The node is ~98% CPU-reserved and each pod requests 20m CPU, so JVM + Java agent startup takes ~65s and probes answer slowly. A bumped `initialDelaySeconds` plus the default 1s timeout was not enough: liveness failed 3x and kubelet killed pods mid-startup | A genuinely broken pod takes up to 5 minutes to be declared failed at startup — acceptable for a dev cluster |
| Scheduled query rules instead of classic metric alerts | Needed for both: 5xx rate requires querying the `requests` table's `resultCode` field (not a pre-aggregated metric), and the crash-loop alert needs `KubeEvents` from Container Insights — neither is a metric-alert-compatible signal | Slightly more setup than a metric alert, but the only mechanism that can express these specific conditions |
