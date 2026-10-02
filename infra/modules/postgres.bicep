@description('Azure region for the server.')
param location string

@description('Tags applied to the server.')
param tags object

@description('Name of the PostgreSQL Flexible Server.')
param serverName string

@description('Administrator login name (not a secret).')
param administratorLogin string

@secure()
@description('Administrator password, generated at deploy time by the caller.')
param administratorPassword string

@description('Single public IP allowed through the firewall (your current IP).')
param allowedClientIp string

@description('AKS cluster\'s outbound public IP, allowed through the firewall so pods can reach the database.')
param aksOutboundIp string

@description('Database names to create on the server.')
param databaseNames array = [
  'customer_db'
  'account_db'
  'card_db'
]

resource server 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  tags: tags
  sku: {
    name: 'Standard_B1ms'
    tier: 'Burstable'
  }
  properties: {
    version: '16'
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorPassword
    storage: {
      storageSizeGB: 32
    }
    backup: {
      backupRetentionDays: 7
      geoRedundantBackup: 'Disabled'
    }
    highAvailability: {
      mode: 'Disabled'
    }
  }
}

resource firewallRule 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: server
  name: 'allow-owner-ip'
  properties: {
    startIpAddress: allowedClientIp
    endIpAddress: allowedClientIp
  }
}

resource aksFirewallRule 'Microsoft.DBforPostgreSQL/flexibleServers/firewallRules@2024-08-01' = {
  parent: server
  name: 'allow-aks-outbound-ip'
  properties: {
    startIpAddress: aksOutboundIp
    endIpAddress: aksOutboundIp
  }
}

resource databases 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = [for dbName in databaseNames: {
  parent: server
  name: dbName
  properties: {
    charset: 'UTF8'
    collation: 'en_US.utf8'
  }
}]

output fqdn string = server.properties.fullyQualifiedDomainName
output serverName string = server.name
