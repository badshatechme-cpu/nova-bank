targetScope = 'subscription'

@description('Environment name, used in resource naming (e.g. dev, prod).')
param environmentName string = 'dev'

@description('Azure region for all resources.')
param location string = 'uaenorth'

@description('Email address to receive budget alerts at 50/80/100% thresholds.')
param ownerEmail string

@description('Monthly budget amount in USD that triggers the 50/80/100% alerts.')
param monthlyBudgetAmount int

@description('GitHub org/user login, e.g. badshatechme-cpu.')
param githubOwner string

@description('GitHub numeric owner (user/org) database ID. Get via: gh api repos/<owner>/<repo> --jq .owner.id')
param githubOwnerId string

@description('GitHub repository name, e.g. nova-bank.')
param githubRepoName string

@description('GitHub numeric repository database ID. Get via: gh api repos/<owner>/<repo> --jq .id')
param githubRepoId string

@description('Branch allowed to deploy via the GitHub Actions identity.')
param githubBranch string = 'main'

@description('Single public IP allowed through the Postgres firewall (your current IP).')
param allowedClientIp string

@description('Object ID of the owner, granted Key Vault Secrets Officer so deployments can write secrets.')
param ownerPrincipalId string

@description('Application ID URI of the NovaBank API Entra ID app registration (Stage 5).')
param entraApiAudience string = ''

// Deterministic (not newGuid()): newGuid() re-evaluates on every `az deployment sub
// create` run, silently rotating these on every unrelated redeploy and desyncing Key
// Vault from the actual Postgres role passwords set by create-db-roles.sh. uniqueString()
// is stable for the life of this resource group, so these stay constant across redeploys
// while still never appearing as a literal in the repo. Mixed case + digit + symbol
// satisfies Postgres's password complexity policy.
@secure()
@description('PostgreSQL admin password, deterministic for this subscription/environment.')
param postgresAdminPassword string = '${toUpper(uniqueString(subscription().id, 'nb-${environmentName}-rg-pg-admin'))}${uniqueString(subscription().id, 'nb-${environmentName}-rg-pg-admin-lower')}!1'

@secure()
@description('customer-service DB password, deterministic for this subscription/environment.')
param customerDbPassword string = '${toUpper(uniqueString(subscription().id, 'nb-${environmentName}-rg-customer-db'))}${uniqueString(subscription().id, 'nb-${environmentName}-rg-customer-db-lower')}!1'

@secure()
@description('account-service DB password, deterministic for this subscription/environment.')
param accountDbPassword string = '${toUpper(uniqueString(subscription().id, 'nb-${environmentName}-rg-account-db'))}${uniqueString(subscription().id, 'nb-${environmentName}-rg-account-db-lower')}!1'

@secure()
@description('card-service DB password, deterministic for this subscription/environment.')
param cardDbPassword string = '${toUpper(uniqueString(subscription().id, 'nb-${environmentName}-rg-card-db'))}${uniqueString(subscription().id, 'nb-${environmentName}-rg-card-db-lower')}!1'

var tags = {
  project: 'novabank'
  env: environmentName
  owner: 'badsha'
  managedBy: 'bicep'
}

resource rg 'Microsoft.Resources/resourceGroups@2024-03-01' = {
  name: 'nb-${environmentName}-rg'
  location: location
  tags: tags
}

module logAnalytics 'modules/logAnalytics.bicep' = {
  name: 'logAnalytics'
  scope: rg
  params: {
    location: location
    tags: tags
    workspaceName: 'nb-${environmentName}-log'
  }
}

module budget 'modules/budget.bicep' = {
  name: 'budget'
  scope: rg
  params: {
    budgetName: 'nb-${environmentName}-budget'
    amount: monthlyBudgetAmount
    ownerEmail: ownerEmail
  }
}

module githubIdentity 'modules/githubIdentity.bicep' = {
  name: 'githubIdentity'
  scope: rg
  params: {
    location: location
    tags: tags
    identityName: 'nb-${environmentName}-id-github'
    githubOwner: githubOwner
    githubOwnerId: githubOwnerId
    githubRepoName: githubRepoName
    githubRepoId: githubRepoId
    githubBranch: githubBranch
  }
}

module aks 'modules/aks.bicep' = {
  name: 'aks'
  scope: rg
  params: {
    location: location
    tags: tags
    clusterName: 'nb-${environmentName}-aks'
    logAnalyticsWorkspaceId: logAnalytics.outputs.workspaceId
    githubIdentityPrincipalId: githubIdentity.outputs.principalId
  }
}

module containerRegistry 'modules/containerRegistry.bicep' = {
  name: 'containerRegistry'
  scope: rg
  params: {
    location: location
    tags: tags
    registryName: 'nbdevacr${uniqueString(rg.id)}'
    githubIdentityPrincipalId: githubIdentity.outputs.principalId
    aksKubeletIdentityObjectId: aks.outputs.kubeletIdentityObjectId
  }
}

module postgres 'modules/postgres.bicep' = {
  name: 'postgres'
  scope: rg
  params: {
    location: location
    tags: tags
    serverName: 'nb-${environmentName}-psql'
    administratorLogin: 'nbadmin'
    administratorPassword: postgresAdminPassword
    allowedClientIp: allowedClientIp
    aksOutboundIp: aks.outputs.outboundIpAddress
  }
}

module keyVault 'modules/keyVault.bicep' = {
  name: 'keyVault'
  scope: rg
  params: {
    location: location
    tags: tags
    vaultName: 'nbdevkv${uniqueString(rg.id)}'
    ownerPrincipalId: ownerPrincipalId
    postgresAdminPassword: postgresAdminPassword
    customerDbPassword: customerDbPassword
    accountDbPassword: accountDbPassword
    cardDbPassword: cardDbPassword
  }
}

module customerServiceIdentity 'modules/serviceIdentity.bicep' = {
  name: 'customerServiceIdentity'
  scope: rg
  params: {
    location: location
    tags: tags
    identityName: 'nb-${environmentName}-id-customer'
    vaultName: keyVault.outputs.vaultName
    secretName: 'customer-db-password'
    aksOidcIssuerUrl: aks.outputs.oidcIssuerUrl
    kubernetesServiceAccountName: 'customer-service'
  }
}

module accountServiceIdentity 'modules/serviceIdentity.bicep' = {
  name: 'accountServiceIdentity'
  scope: rg
  params: {
    location: location
    tags: tags
    identityName: 'nb-${environmentName}-id-account'
    vaultName: keyVault.outputs.vaultName
    secretName: 'account-db-password'
    aksOidcIssuerUrl: aks.outputs.oidcIssuerUrl
    kubernetesServiceAccountName: 'account-service'
  }
}

module cardServiceIdentity 'modules/serviceIdentity.bicep' = {
  name: 'cardServiceIdentity'
  scope: rg
  params: {
    location: location
    tags: tags
    identityName: 'nb-${environmentName}-id-card'
    vaultName: keyVault.outputs.vaultName
    secretName: 'card-db-password'
    aksOidcIssuerUrl: aks.outputs.oidcIssuerUrl
    kubernetesServiceAccountName: 'card-service'
  }
}

// The ingress IP is hardcoded here, not derived as a Bicep output, because it's
// auto-assigned by the App Routing add-on after AKS deploys — not knowable at
// Bicep-authoring time. If the cluster is ever rebuilt, update these hostnames
// (and the Helm values-*.yaml ingress.host values) to match the new IP.
module apim 'modules/apim.bicep' = {
  name: 'apim'
  scope: rg
  params: {
    location: location
    tags: tags
    apimName: 'nb-${environmentName}-apim'
    publisherEmail: ownerEmail
    publisherName: 'NovaBank'
    tenantId: subscription().tenantId
    apiAudience: entraApiAudience
    customerServiceUrl: 'http://customer.20.233.234.238.nip.io'
    accountServiceUrl: 'http://account.20.233.234.238.nip.io'
    cardServiceUrl: 'http://card.20.233.234.238.nip.io'
  }
}

output resourceGroupName string = rg.name
output apimGatewayUrl string = apim.outputs.gatewayUrl
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
output acrLoginServer string = containerRegistry.outputs.loginServer
output acrName string = containerRegistry.outputs.registryName
output githubIdentityClientId string = githubIdentity.outputs.clientId
output postgresFqdn string = postgres.outputs.fqdn
output keyVaultName string = keyVault.outputs.vaultName
output keyVaultUri string = keyVault.outputs.vaultUri
output aksClusterName string = aks.outputs.clusterName
output aksOidcIssuerUrl string = aks.outputs.oidcIssuerUrl
output customerIdentityClientId string = customerServiceIdentity.outputs.clientId
output accountIdentityClientId string = accountServiceIdentity.outputs.clientId
output cardIdentityClientId string = cardServiceIdentity.outputs.clientId
