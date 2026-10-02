@description('Azure region for the identity.')
param location string

@description('Tags applied to the identity.')
param tags object

@description('Name of the user-assigned managed identity.')
param identityName string

@description('GitHub org/user login, e.g. badshatechme-cpu.')
param githubOwner string

@description('GitHub numeric owner (user/org) database ID — stable across renames. Get via: gh api repos/<owner>/<repo> --jq .owner.id')
param githubOwnerId string

@description('GitHub repository name, e.g. nova-bank.')
param githubRepoName string

@description('GitHub numeric repository database ID — stable across renames. Get via: gh api repos/<owner>/<repo> --jq .id')
param githubRepoId string

@description('Branch allowed to exchange a GitHub OIDC token for an Azure token via this identity.')
param githubBranch string = 'main'

resource identity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: identityName
  location: location
  tags: tags
}

// GitHub's OIDC subject claim is keyed on the owner/repo's stable numeric database IDs,
// not their current names, so a repo or org rename never silently changes who can authenticate.
resource federatedCredential 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: identity
  name: 'github-actions-${githubBranch}'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    audiences: [
      'api://AzureADTokenExchange'
    ]
    subject: 'repo:${githubOwner}@${githubOwnerId}/${githubRepoName}@${githubRepoId}:ref:refs/heads/${githubBranch}'
  }
}

output principalId string = identity.properties.principalId
output clientId string = identity.properties.clientId
