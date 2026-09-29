# NovaBank Platform (Project 2) — Context for Claude Code

## What this is
Project 2 deploys the NovaBank core services (customer, account, card) to Azure in
seven stages, each leaving a working, documented platform. The target is a
**banking-grade reference platform**: infrastructure as code, no secrets in code,
least privilege, private networking, observability, and cost control.

Read the root `CLAUDE.md` for application rules. This file adds platform rules.

## Owner & working style
- The owner is an enterprise architect learning hands-on Azure delivery.
- **One stage at a time, one step at a time.** Plan first, then implement.
- Give terminal commands **one at a time** and wait for the output before the next.
- Explain every Azure resource you create in one or two sentences: what it is, why we need it.

## Non-negotiable rules
1. **Infrastructure as code only (Bicep).** Never tell the owner to create or change
   resources in the Azure portal. The portal is for viewing only.
2. **Always run `what-if` before any deployment** and show the result for review:
   `az deployment group what-if ...` / `az deployment sub what-if ...`
3. **No secrets in the repo, ever.** No passwords, keys, or connection strings in code,
   YAML, Helm values, or GitHub. Use Key Vault, managed identities, and OIDC.
4. **Least privilege.** Scope every role assignment to the smallest resource that works.
   Prefer built-in roles. Explain each role assignment.
5. **Cost guardrails.** Use the smallest SKUs that work. **Ask before creating anything
   expensive or slow to provision** (API Management, Premium SKUs, extra node pools,
   private endpoints on Premium-only services). Mention approximate cost impact and let
   the owner decide.
6. **Tag everything:** `project=novabank`, `env=dev`, `owner=badsha`, `managedBy=bicep`.

## Naming convention
`nb-<env>-<resource-abbreviation>[-<purpose>]`, e.g. `nb-dev-rg`, `nb-dev-aks`,
`nb-dev-kv`, `nb-dev-psql`. Where Azure requires globally unique, alphanumeric names
(ACR, Key Vault, storage), use `nbdev<type><4-char-suffix>`.

## Repository layout (additions to novabank-core)
```
novabank-core/
├── infra/
│   ├── main.bicep              # subscription-scope entry point
│   ├── modules/                # one module per resource type
│   └── env/dev.bicepparam      # environment parameters
├── deploy/
│   └── helm/novabank-service/  # ONE shared chart; values per service
│       ├── values-customer.yaml
│       ├── values-account.yaml
│       └── values-card.yaml
├── scripts/
│   ├── start.sh                # start AKS + PostgreSQL
│   └── stop.sh                 # stop AKS + PostgreSQL (save cost)
├── .github/workflows/          # build + deploy pipelines
└── docs/platform/
    ├── CLAUDE-PLATFORM.md
    ├── BUILD_PLAN_PLATFORM.md
    ├── architecture.md         # diagrams (Mermaid) + decisions, updated each stage
    ├── runbook.md              # how to start, stop, deploy, troubleshoot
    └── cost-log.md             # cost observed per stage (feeds the FinOps project)
```

## Two deployment paths (important separation)
- **Platform changes** (networks, AKS, databases, Key Vault, APIM, role assignments):
  deployed **manually by the owner** with Bicep from his machine, after `what-if`.
- **Application changes** (container images, Helm releases): deployed by **GitHub Actions**
  using OIDC federation. The pipeline identity can push images and deploy to the cluster,
  but **cannot create infrastructure or assign roles.**

This mirrors how regulated banks separate platform and application change.

## Security model (target state by Stage 5)
- APIM validates the Entra ID JWT (issuer, audience, scopes) — first layer.
- Each service validates the JWT again with Spring Security resource server — second layer
  (defence in depth; never trust the gateway alone).
- **Ownership check:** the `customerId` claim in the token must equal `{customerId}` in the
  path; otherwise return 404 (not 403, to avoid confirming the resource exists).
- Tokens with the app role `NovaBank.Staff` may access any customer (back-office use).
- Claim name and role name are configuration, not hard-coded.

## Documentation habit
At the end of every stage, update `architecture.md`, `runbook.md`, and `cost-log.md`.
These documents are portfolio deliverables, not afterthoughts.
