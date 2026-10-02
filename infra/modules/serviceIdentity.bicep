@description('Azure region for the identity.')
param location string

@description('Tags applied to the identity.')
param tags object

@description('Name of the user-assigned managed identity for this service.')
param identityName string

@description('Name of the Key Vault holding this service\'s secret.')
param vaultName string

@description('Name of the single secret this service is allowed to read.')
param secretName string

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
  tags: tags
}

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' existing = {
  name: vaultName
}

resource secret 'Microsoft.KeyVault/vaults/secrets@2023-07-01' existing = {
  parent: vault
  name: secretName
}

var secretsUserRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4633458b-17de-408a-b874-0445c86b69e6')

// Scoped to this one secret, not the vault — this identity cannot read any other
// service's password or the Postgres admin password.
resource secretAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(secret.id, identity.id, secretsUserRoleId)
  scope: secret
  properties: {
    roleDefinitionId: secretsUserRoleId
    principalId: identity.properties.principalId
    principalType: 'ServicePrincipal'
  }
}

output principalId string = identity.properties.principalId
output clientId string = identity.properties.clientId
