@description('Azure region for Application Insights.')
param location string

@description('Tags applied to Application Insights.')
param tags object

@description('Name of the Application Insights resource.')
param appInsightsName string

@description('Resource ID of the Log Analytics workspace to link (workspace-based mode).')
param logAnalyticsWorkspaceId string

// Workspace-based (not classic) Application Insights: ingestion lands in the same
// Log Analytics workspace Container Insights already uses, sharing the 1GB/day cap set
// in Stage 4 rather than adding a second, uncapped data store.
resource appInsights 'Microsoft.Insights/components@2020-02-02' = {
  name: appInsightsName
  location: location
  tags: tags
  kind: 'java'
  properties: {
    Application_Type: 'java'
    WorkspaceResourceId: logAnalyticsWorkspaceId
    IngestionMode: 'LogAnalytics'
  }
}

output connectionString string = appInsights.properties.ConnectionString
output appInsightsId string = appInsights.id
output appInsightsName string = appInsights.name
