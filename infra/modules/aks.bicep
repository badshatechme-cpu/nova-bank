@description('Azure region for the cluster.')
param location string

@description('Tags applied to the cluster.')
param tags object

@description('Name of the AKS cluster.')
param clusterName string

@description('Resource ID of the Log Analytics workspace for Container Insights.')
param logAnalyticsWorkspaceId string

@description('Principal ID of the GitHub Actions pipeline identity, granted cluster-user access to deploy.')
param githubIdentityPrincipalId string

// Explicitly provisioned (rather than letting AKS auto-create one) so its address is
// a known Bicep output we can feed straight into the Postgres firewall rule — pods
// reach Postgres through this IP, not through the owner's own IP.
resource outboundIp 'Microsoft.Network/publicIPAddresses@2023-11-01' = {
  name: '${clusterName}-outbound-ip'
  location: location
  tags: tags
  sku: {
    name: 'Standard'
  }
  properties: {
    publicIPAllocationMethod: 'Static'
  }
}

resource aks 'Microsoft.ContainerService/managedClusters@2024-08-01' = {
  name: clusterName
  location: location
  tags: tags
  sku: {
    name: 'Base'
    tier: 'Free'
  }
  identity: {
    type: 'SystemAssigned'
  }
  properties: {
    dnsPrefix: clusterName
    agentPoolProfiles: [
      {
        name: 'system'
        count: 1
        vmSize: 'Standard_D2als_v6'
        mode: 'System'
        osType: 'Linux'
      }
    ]
    networkProfile: {
      loadBalancerSku: 'standard'
      outboundType: 'loadBalancer'
      loadBalancerProfile: {
        outboundIPs: {
          publicIPs: [
            {
              id: outboundIp.id
            }
          ]
        }
      }
    }
    oidcIssuerProfile: {
      enabled: true
    }
    securityProfile: {
      workloadIdentity: {
        enabled: true
      }
    }
    addonProfiles: {
      azureKeyvaultSecretsProvider: {
        enabled: true
        config: {
          enableSecretRotation: 'false'
        }
      }
      omsagent: {
        enabled: true
        config: {
          logAnalyticsWorkspaceResourceID: logAnalyticsWorkspaceId
        }
      }
    }
    ingressProfile: {
      webAppRouting: {
        enabled: true
      }
    }
  }
}

var clusterUserRoleId = subscriptionResourceId('Microsoft.Authorization/roleDefinitions', '4abbcc35-e782-43d8-92c5-2d3f1bd2253f')

// Lets the pipeline identity fetch a kubeconfig and deploy, and nothing else in the subscription.
resource pipelineClusterAccess 'Microsoft.Authorization/roleAssignments@2022-04-01' = {
  name: guid(aks.id, githubIdentityPrincipalId, clusterUserRoleId)
  scope: aks
  properties: {
    roleDefinitionId: clusterUserRoleId
    principalId: githubIdentityPrincipalId
    principalType: 'ServicePrincipal'
  }
}

output oidcIssuerUrl string = aks.properties.oidcIssuerProfile.issuerURL
output kubeletIdentityObjectId string = aks.properties.identityProfile.kubeletidentity.objectId
output clusterName string = aks.name
output id string = aks.id
output outboundIpAddress string = outboundIp.properties.ipAddress
