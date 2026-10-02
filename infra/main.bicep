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

@secure()
@description('PostgreSQL admin password, generated at deploy time.')
param postgresAdminPassword string = newGuid()

@secure()
@description('customer-service DB password, generated at deploy time.')
param customerDbPassword string = newGuid()

@secure()
@description('account-service DB password, generated at deploy time.')
param accountDbPassword string = newGuid()

@secure()
@description('card-service DB password, generated at deploy time.')
param cardDbPassword string = newGuid()

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

output resourceGroupName string = rg.name
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
