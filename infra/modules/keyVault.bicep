@description('Azure region for the vault.')
param location string

@description('Tags applied to the vault.')
param tags object

@description('Name of the Key Vault (must be globally unique).')
param vaultName string

@description('Object ID of the owner, granted Key Vault Secrets Officer so this deployment can write secrets.')
param ownerPrincipalId string

@secure()
param postgresAdminPassword string

@secure()
param customerDbPassword string

@secure()
param accountDbPassword string

@secure()
param cardDbPassword string

@secure()
@description('Application Insights connection string, shared by all three services.')
param appInsightsConnectionString string

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: vaultName
  location: location
  tags: tags
  properties: {
    sku: {
      family: 'A'
      name: 'standard'
    }
    tenantId: subscription().tenantId
    enableRbacAuthorization: true
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
  }
}

var secretsOfficerRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', 'b86a8fe4-44ce-4948-aee5-eccb2c155cd7')

// Key Vault RBAC mode grants nobody access by default — the deploying owner needs this
// to let this same deployment write the secrets below.
resource ownerSecretsOfficer 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(vault.id, ownerPrincipalId, secretsOfficerRoleId)
  scope: vault
  properties: {
    roleDefinitionId: secretsOfficerRoleId
    principalId: ownerPrincipalId
    principalType: 'User'
  }
}

resource adminPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'postgres-admin-password'
  properties: {
    value: postgresAdminPassword
  }
  dependsOn: [
    ownerSecretsOfficer
  ]
}

resource customerDbPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'customer-db-password'
  properties: {
    value: customerDbPassword
  }
  dependsOn: [
    ownerSecretsOfficer
  ]
}

resource accountDbPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'account-db-password'
  properties: {
    value: accountDbPassword
  }
  dependsOn: [
    ownerSecretsOfficer
  ]
}

resource cardDbPasswordSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'card-db-password'
  properties: {
    value: cardDbPassword
  }
  dependsOn: [
    ownerSecretsOfficer
  ]
}

resource appInsightsConnectionStringSecret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' = {
  parent: vault
  name: 'appinsights-connection-string'
  properties: {
    value: appInsightsConnectionString
  }
  dependsOn: [
    ownerSecretsOfficer
  ]
}

output vaultName string = vault.name
output vaultUri string = vault.properties.vaultUri
output customerSecretId string = customerDbPasswordSecret.id
output accountSecretId string = accountDbPasswordSecret.id
output cardSecretId string = cardDbPasswordSecret.id
output appInsightsSecretId string = appInsightsConnectionStringSecret.id
