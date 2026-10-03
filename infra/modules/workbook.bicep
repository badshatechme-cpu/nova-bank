@description('Azure region for the workbook.')
param location string

@description('Tags applied to the workbook.')
param tags object

@description('Resource ID of the Application Insights component (request rate/error rate/latency queries).')
param appInsightsId string

@description('Resource ID of the Log Analytics workspace (pod restart query, via Container Insights data).')
param logAnalyticsWorkspaceId string

// Defined as a Bicep object rather than a raw JSON string — avoids the quote-escaping
// problems hit building APIM's policies, since Bicep serializes this correctly via string().
var workbookContent = {
  version: 'Notebook/1.0'
  items: [
    {
      type: 1
      content: {
        json: '# NovaBank Platform Observability\n\nRequest rate, error rate, latency, and pod restarts across all three services. Defaults to the last hour.'
      }
    }
    {
      type: 3
      content: {
        version: 'KqlItem/1.0'
        query: 'requests\n| summarize RequestsPerMin = count() by bin(timestamp, 1m), cloud_RoleName\n| render timechart'
        size: 0
        title: 'Request rate by service'
        timeContext: {
          durationMs: 3600000
        }
        queryType: 0
        resourceType: 'microsoft.insights/components'
      }
    }
    {
      type: 3
      content: {
        version: 'KqlItem/1.0'
        query: 'requests\n| summarize Total = count(), Errors = countif(toint(resultCode) >= 500) by bin(timestamp, 5m), cloud_RoleName\n| extend ErrorRatePct = round(100.0 * Errors / Total, 2)\n| project timestamp, cloud_RoleName, ErrorRatePct\n| render timechart'
        size: 0
        title: 'Error rate (%) by service'
        timeContext: {
          durationMs: 3600000
        }
        queryType: 0
        resourceType: 'microsoft.insights/components'
      }
    }
    {
      type: 3
      content: {
        version: 'KqlItem/1.0'
        query: 'requests\n| summarize AvgDurationMs = avg(duration), P95DurationMs = percentile(duration, 95) by bin(timestamp, 5m), cloud_RoleName\n| render timechart'
        size: 0
        title: 'Latency (avg / P95 ms) by service'
        timeContext: {
          durationMs: 3600000
        }
        queryType: 0
        resourceType: 'microsoft.insights/components'
      }
    }
    {
      type: 3
      content: {
        version: 'KqlItem/1.0'
        query: 'KubePodInventory\n| where Namespace == "novabank"\n| summarize MaxRestarts = max(PodRestartCount) by bin(TimeGenerated, 5m), Name\n| render timechart'
        size: 0
        title: 'Pod restarts (novabank namespace)'
        timeContext: {
          durationMs: 3600000
        }
        queryType: 0
        resourceType: 'microsoft.operationalinsights/workspaces'
      }
    }
  ]
  fallbackResourceIds: [
    appInsightsId
    logAnalyticsWorkspaceId
  ]
  '$schema': 'https://github.com/Microsoft/Application-Insights-Workbooks/blob/master/schema/workbook.json'
}

resource workbook 'Microsoft.Insights/workbooks@2022-04-01' = {
  name: guid('nb-dev-observability-workbook')
  location: location
  tags: tags
  kind: 'shared'
  properties: {
    displayName: 'NovaBank Platform Observability'
    serializedData: string(workbookContent)
    category: 'workbook'
    sourceId: appInsightsId
  }
}

output workbookId string = workbook.id
