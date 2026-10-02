@description('Azure region for the identity.')
param location string

@description('Tags applied to the identity.')
param tags object

@description('Name of the user-assigned managed identity.')
param identityName string

@description('GitHub repository in "owner/repo" form, e.g. badshatechme-cpu/nova-bank.')
param githubRepo string

@description('Branch allowed to exchange a GitHub OIDC token for an Azure token via this identity.')
param githubBranch string = 'main'

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
  tags: tags
}

resource federatedCredential 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: identity
  name: 'github-actions-${githubBranch}'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    audiences: [
      'api://AzureADTokenExchange'
    ]
    subject: 'repo:${githubRepo}:ref:refs/heads/${githubBranch}'
  }
}

output principalId string = identity.properties.principalId
output clientId string = identity.properties.clientId
