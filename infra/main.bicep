targetScope = 'subscription'

@description('Environment name, used in resource naming (e.g. dev, prod).')
param environmentName string = 'dev'

@description('Azure region for all resources.')
param location string = 'uaenorth'

@description('Email address to receive budget alerts at 50/80/100% thresholds.')
param ownerEmail string

@description('Monthly budget amount in USD that triggers the 50/80/100% alerts.')
param monthlyBudgetAmount int

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

output resourceGroupName string = rg.name
output logAnalyticsWorkspaceId string = logAnalytics.outputs.workspaceId
