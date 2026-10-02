@description('Azure region for the workspace.')
param location string

@description('Tags applied to the workspace.')
param tags object

@description('Name of the Log Analytics workspace.')
param workspaceName string

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: workspaceName
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: 30
    // Hard ceiling on daily ingestion cost. This dev cluster generates well under
    // 0.1GB/day in practice — 1GB/day leaves headroom while capping worst-case exposure
    // if logging volume ever spikes unexpectedly.
    workspaceCapping: {
      dailyQuotaGb: 1
    }
  }
}

output workspaceId string = workspace.id
output workspaceCustomerId string = workspace.properties.customerId
