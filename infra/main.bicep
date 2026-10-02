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

module containerRegistry 'modules/containerRegistry.bicep' = {
  name: 'containerRegistry'
  scope: rg
  params: {
    location: location
    tags: tags
    registryName: 'nbdevacr${uniqueString(rg.id)}'
    githubIdentityPrincipalId: githubIdentity.outputs.principalId
  }
}

output resourceGroupName string = rg.name
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
output acrLoginServer string = containerRegistry.outputs.loginServer
output acrName string = containerRegistry.outputs.registryName
output githubIdentityClientId string = githubIdentity.outputs.clientId
