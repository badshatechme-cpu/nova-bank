@description('Azure region for the registry.')
param location string

@description('Tags applied to the registry.')
param tags object

@description('Name of the container registry (must be globally unique, alphanumeric).')
param registryName string

@description('Principal ID of the identity to grant AcrPush on this registry only.')
param githubIdentityPrincipalId string

@description('Object ID of the AKS cluster\'s kubelet identity, granted AcrPull on this registry only.')
param aksKubeletIdentityObjectId string

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: registryName
  location: location
  tags: tags
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
  }
}

// AcrPush built-in role, scoped to this registry only: push/pull images, nothing else.
// No access to other registries, registry settings, or any other resource in the subscription.
var acrPushRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '8311e382-0749-4cb8-b61a-304f252e45ec')

resource acrPushAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, githubIdentityPrincipalId, acrPushRoleId)
  scope: registry
  properties: {
    roleDefinitionId: acrPushRoleId
    principalId: githubIdentityPrincipalId
    principalType: 'ServicePrincipal'
  }
}

// AcrPull built-in role, scoped to this registry only: AKS nodes can pull images here
// and nothing else.
var acrPullRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '7f951dda-4ed3-4680-a7ca-43fe172d538d')

resource acrPullAssignment 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(registry.id, aksKubeletIdentityObjectId, acrPullRoleId)
  scope: registry
  properties: {
    roleDefinitionId: acrPullRoleId
    principalId: aksKubeletIdentityObjectId
    principalType: 'ServicePrincipal'
  }
}

output loginServer string = registry.properties.loginServer
output registryName string = registry.name
