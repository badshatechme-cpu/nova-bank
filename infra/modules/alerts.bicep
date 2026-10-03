@description('Azure region for the alert resources (scheduledQueryRules only; action groups are global).')
param location string

@description('Tags applied to the alert resources.')
param tags object

@description('Resource ID of the Application Insights component (5xx rate alert).')
param appInsightsId string

@description('Resource ID of the Log Analytics workspace (pod crash-loop alert, via Container Insights data).')
param logAnalyticsWorkspaceId string

@description('Email address the action group notifies when an alert fires.')
param ownerEmail string

resource actionGroup 'Microsoft.Insights/actionGroups@2023-01-01' = {
  name: 'nb-dev-alerts'
  location: 'global'
  tags: tags
  properties: {
    groupShortName: 'novabank'
    enabled: true
    emailReceivers: [
      {
        name: 'owner'
        emailAddress: ownerEmail
        useCommonAlertSchema: true
      }
    ]
  }
}

resource fiveXxRateAlert 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'nb-dev-5xx-rate-alert'
  location: location
  tags: tags
  properties: {
    displayName: '5xx error rate above threshold'
    description: 'Fires when any service returns more than 5 server errors in a 5-minute window.'
    severity: 2
    enabled: true
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    scopes: [
      appInsightsId
    ]
    criteria: {
      allOf: [
        {
          query: 'requests | where toint(resultCode) >= 500'
          timeAggregation: 'Count'
          operator: 'GreaterThan'
          threshold: 5
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
    autoMitigate: true
  }
}

resource podCrashLoopAlert 'Microsoft.Insights/scheduledQueryRules@2023-03-15-preview' = {
  name: 'nb-dev-pod-crashloop-alert'
  location: location
  tags: tags
  properties: {
    displayName: 'Pod crash loop detected'
    description: 'Fires when a pod in the novabank namespace emits a BackOff event, the signature of CrashLoopBackOff.'
    severity: 1
    enabled: true
    evaluationFrequency: 'PT5M'
    windowSize: 'PT5M'
    scopes: [
      logAnalyticsWorkspaceId
    ]
    criteria: {
      allOf: [
        {
          query: 'KubeEvents | where Namespace == "novabank" and Reason == "BackOff"'
          timeAggregation: 'Count'
          operator: 'GreaterThanOrEqual'
          threshold: 1
          failingPeriods: {
            numberOfEvaluationPeriods: 1
            minFailingPeriodsToAlert: 1
          }
        }
      ]
    }
    actions: {
      actionGroups: [
        actionGroup.id
      ]
    }
    autoMitigate: true
  }
}

output actionGroupId string = actionGroup.id
