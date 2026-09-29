# NovaBank Platform (Project 2) — Build Plan

Seven stages plus prerequisites. Each stage ends with a working platform, updated docs,
and a git commit. Start every stage in **plan mode**:
`claude --permission-mode plan`, then paste the stage prompt.

---

## Target architecture (end of Stage 7)

```
 Internet
    │
    ▼
 ┌─────────────────────┐   validates JWT (layer 1)
 │  API Management     │◄──────────── Entra ID (tokens)
 └─────────┬───────────┘
           │ private VNet
 ┌─────────▼─────────────────────────────────────────┐
 │  AKS  (workload identity, managed ingress)        │
 │   customer-svc   account-svc   card-svc           │  validate JWT (layer 2)
 └───────┬──────────────┬──────────────┬─────────────┘  + ownership check
         │ private endpoints
 ┌───────▼────────┐ ┌───▼─────────┐ ┌──────────────┐
 │ PostgreSQL     │ │ Key Vault   │ │ Container    │
 │ Flexible Server│ │             │ │ Registry     │
 └────────────────┘ └─────────────┘ └──────────────┘
         all telemetry ──► Application Insights / Log Analytics

 GitHub Actions ──(OIDC, no secrets)──► ACR push + Helm deploy
```

## Key decisions and trade-offs
| Decision | Why | Trade-off |
|---|---|---|
| Bicep (not Terraform) | Azure-native, no state file to manage, easiest to learn | Azure-only; Terraform can be a later exercise |
| Monorepo (app + infra) | One place to learn; simple for a solo builder | Real banks usually split platform and app repos |
| AKS (not Container Apps) | Matches your OCP→AKS career story and client demand | Higher cost and complexity; mitigated by stop/start |
| One shared Helm chart | Three services are identical in shape; shows reuse | Less flexibility per service |
| Platform manual, apps via pipeline | Separation of duties, like regulated change control | Platform changes need you at the keyboard |
| OIDC federation for GitHub | No stored cloud credentials anywhere | Slightly more setup |
| Public first, private in Stage 7 | Learn each service before adding network complexity | Temporary exposure; restrict with firewall rules and stop resources when idle |

---

## Stage 0 — Prerequisites (on your Mac)

**Prompt:**
> Read docs/platform/CLAUDE-PLATFORM.md. Check my Mac has everything needed for this
> project: Azure CLI (logged in, correct subscription), Bicep, kubectl, Helm, Docker,
> and GitHub CLI. Give me one check command at a time and wait for my output.
> Install anything missing with Homebrew, one command at a time.

**Done when:** all tools present; `az account show` shows your demo subscription.

---

## Stage 1 — Foundations

**Prompt:**
> Implement Stage 1. Create infra/main.bicep at subscription scope with modules for:
> resource group nb-dev-rg, Log Analytics workspace, and a monthly budget with alerts at
> 50%, 80% and 100% to my email (parameter). Apply the naming and tagging rules.
> Create dev.bicepparam. Run what-if, show me the result, wait for approval, then deploy.
> Start docs/platform/architecture.md, runbook.md and cost-log.md.

**Done when:** RG, workspace, and budget exist; you receive the budget confirmation.
**Learn:** subscription vs resource-group scope, what-if, tags, cost guardrails.
`git commit -m "Platform stage 1: foundations"`

---

## Stage 2 — Container registry + build pipeline

**Prompt:**
> Implement Stage 2. Add an Azure Container Registry (Basic SKU) module. Create an Entra ID
> app registration (or user-assigned managed identity) for GitHub Actions with a federated
> credential for my repo's main branch, and grant it AcrPush on the registry only.
> Create .github/workflows/build.yml that builds the three service images on push to main,
> tags them with the git SHA, and pushes to ACR. No secrets stored in GitHub except
> non-sensitive IDs (tenant, subscription, client ID) as repository variables.

**Done when:** a push to main produces three images in ACR tagged with the commit SHA.
**Learn:** OIDC federation, why no stored credentials, image tagging strategy.
`git commit -m "Platform stage 2: ACR and build pipeline"`

---

## Stage 3 — Database and secrets

**Prompt:**
> Implement Stage 3. Add PostgreSQL Flexible Server (smallest Burstable SKU, public access
> restricted by firewall rule to my IP for now) with three databases: customer_db,
> account_db, card_db. Add Key Vault in RBAC mode. Generate the admin and per-service DB
> passwords at deploy time and store them only in Key Vault. Create one user-assigned
> managed identity per service and give each "Key Vault Secrets User" on its own secrets
> only. Add stop/start of PostgreSQL to scripts/stop.sh and start.sh.

**Done when:** databases exist; secrets are in Key Vault; nothing sensitive is in the repo.
**Learn:** Key Vault RBAC, secret-level scoping, managed identities.
**Stretch (optional):** passwordless Entra ID authentication from Spring Boot to PostgreSQL.
`git commit -m "Platform stage 3: PostgreSQL and Key Vault"`

---

## Stage 4 — AKS and first deployment

**Prompt:**
> Implement Stage 4. Add an AKS cluster: one small system node pool, OIDC issuer and
> workload identity enabled, Secrets Store CSI driver add-on, managed ingress (application
> routing add-on), Container Insights to the Log Analytics workspace, and AcrPull for the
> cluster on the registry. Federate each service's managed identity with its Kubernetes
> service account. Create the shared Helm chart with: liveness/readiness probes on
> Actuator, resource requests/limits, secrets mounted from Key Vault via CSI, and one
> values file per service. Add .github/workflows/deploy.yml that deploys with Helm after a
> successful build (the pipeline identity gets only the AKS role it needs to deploy).
> Add AKS stop/start to the scripts.

**Done when:** all three services are running in AKS, Flyway has migrated the Azure
databases, the data generator can seed them, and ingress returns data.
**Learn:** workload identity end to end, CSI secrets, probes, Helm values, pipeline deploys.
`git commit -m "Platform stage 4: AKS deployment"`

---

## Stage 5 — API Management + Entra ID security

**Prompt:**
> Implement Stage 5. Ask me to confirm the APIM tier first (consider provisioning time,
> cost, and whether Stage 7 VNet integration is supported). Then:
> (1) Entra ID: app registration "NovaBank API" exposing scopes accounts.read,
> cards.read, cards.write, transfers.write and app role NovaBank.Staff; a directory
> extension attribute customerId on test users, emitted as an optional claim in the API's
> access tokens; two test users mapped to two seeded customers.
> (2) APIM: import the three OpenAPI specs as one API product, validate the Entra JWT
> (issuer, audience, required scopes per operation), add a rate limit, route to AKS ingress.
> (3) Services: add Spring Security resource server with the ownership check and staff
> role rule described in CLAUDE-PLATFORM.md, with tests.
> (4) A .http file showing: own data works, another customer's data returns 404, missing
> scope returns 403, no token returns 401.

**Done when:** all four .http scenarios behave correctly through APIM.
**Learn:** scopes vs roles, token claims, gateway vs service validation, defence in depth.
`git commit -m "Platform stage 5: APIM and Entra ID"`

---

## Stage 6 — Observability

**Prompt:**
> Implement Stage 6. Attach the Application Insights Java agent (OpenTelemetry-based) to
> each service image, connected via configuration (no connection strings in code). Build
> an Azure Monitor workbook showing request rate, error rate, latency per service, and
> pod restarts. Create alerts for 5xx rate above a threshold and pod crash loops. Show me
> an end-to-end trace from APIM through a service to PostgreSQL.

**Done when:** you can follow one request end to end and an alert fires in a test.
**Learn:** distributed tracing, golden signals, alert design.
`git commit -m "Platform stage 6: observability"`

---

## Stage 7 — Private networking (banking-grade hardening)

**Prompt:**
> Implement Stage 7. Explain the target network design and cost impact first and wait for
> approval. Add a VNet with subnets for AKS, APIM, PostgreSQL and private endpoints, with
> NSGs. Move PostgreSQL to private access, add a private endpoint for Key Vault with
> private DNS zones, integrate APIM with the VNet, and restrict the AKS API server to
> authorized IPs. Remove all public database access. Update architecture.md with a network
> diagram and a table of every inbound path and why it exists.

**Done when:** the only public entry point is APIM, and the databases are unreachable
from the internet.
**Learn:** private endpoints, private DNS, NSGs, the "only one front door" principle.
`git commit -m "Platform stage 7: private networking"`

---

## Cost discipline (every stage)
- Run `scripts/stop.sh` whenever you finish a session.
- Record observed cost per stage in `cost-log.md` — this becomes the raw material for
  the FinOps review (Project 6).
- API Management cannot be stopped, only deleted, and takes a long time to provision.
  Decide in Stage 5 whether to keep it running or recreate it for demos.

## Portfolio outputs from this project
- Architecture document with diagrams and decision tables
- Runbook (start, stop, deploy, rollback, troubleshoot)
- Security model write-up (the four .http scenarios are a perfect demo)
- Cost log
- A 5-minute demo video: push code → pipeline → running on AKS → secured call via APIM
