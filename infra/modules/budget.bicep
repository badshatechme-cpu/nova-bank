@description('Name of the budget.')
param budgetName string

@description('Monthly budget amount in USD.')
param amount int

@description('Email address to notify at 50/80/100% of the budget.')
param ownerEmail string

@description('Start of the first budget period (first day of current month, UTC). Defaults to now; left as a param so what-if stays deterministic across re-runs in the same month.')
param startDate string = utcNow('yyyy-MM-01T00:00:00Z')

var endDate = dateTimeAdd(startDate, 'P5Y')

resource budget 'Microsoft.Consumption/budgets@2023-11-01' = {
  name: budgetName
  properties: {
    category: 'Cost'
    amount: amount
    timeGrain: 'Monthly'
    timePeriod: {
      startDate: startDate
      endDate: endDate
    }
    notifications: {
      Alert50Pct: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 50
        contactEmails: [
          ownerEmail
        ]
        thresholdType: 'Actual'
      }
      Alert80Pct: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 80
        contactEmails: [
          ownerEmail
        ]
        thresholdType: 'Actual'
      }
      Alert100Pct: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        contactEmails: [
          ownerEmail
        ]
        thresholdType: 'Actual'
      }
    }
  }
}
