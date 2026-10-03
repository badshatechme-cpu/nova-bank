@description('Azure region for APIM.')
param location string

@description('Tags applied to APIM.')
param tags object

@description('Name of the APIM instance.')
param apimName string

@description('Email that receives APIM service notifications (not sent to automatically).')
param publisherEmail string

@description('Publisher name shown on the developer portal.')
param publisherName string

@description('Entra ID tenant ID, used to build the expected JWT issuer.')
param tenantId string

@description('Expected JWT audience: the API app registration Application ID URI.')
param apiAudience string

@description('Base URL of customer-service through its ingress hostname.')
param customerServiceUrl string

@description('Base URL of account-service through its ingress hostname.')
param accountServiceUrl string

@description('Base URL of card-service through its ingress hostname.')
param cardServiceUrl string

resource apim 'Microsoft.ApiManagement/service@2023-05-01-preview' = {
  name: apimName
  location: location
  tags: tags
  sku: {
    name: 'Consumption'
    capacity: 0
  }
  properties: {
    publisherEmail: publisherEmail
    publisherName: publisherName
  }
}

// Hardcoded rather than using environment() deliberately: environment().authentication
// .loginEndpoint can return the legacy login.windows.net alias, which may not exactly
// match the "iss" claim Entra ID actually puts in v2 tokens. Getting this wrong would
// silently break all JWT validation, so explicit and verified beats generic.
#disable-next-line no-hardcoded-env-urls
var issuer = 'https://login.microsoftonline.com/${tenantId}/v2.0'

// Bicep's triple-quoted multi-line strings do NOT interpolate ${...} — they're raw
// literals. Placeholder tokens are substituted below via replace() instead.

// First layer of defence-in-depth: issuer + audience only here. Specific scope
// requirements are enforced per-API/per-operation below, and the ownership check
// (customerId claim vs path) happens again at the service layer — APIM is never
// the only thing standing between a token and the data.
// output-token-variable-name stores the parsed token as a "jwt" context variable, which
// stays available to the API-level scope-check policies below — they run later in the same
// request's pipeline (global -> product -> api -> operation) and read this instead of
// re-validating the token from scratch.
var baseJwtValidationPolicyRaw = '''
<policies>
  <inbound>
    <base />
    <validate-jwt header-name="Authorization" failed-validation-httpcode="401" failed-validation-error-message="Missing or invalid token" require-expiration-time="true" require-signed-tokens="true" output-token-variable-name="jwt">
      <openid-config url="__ISSUER__/.well-known/openid-configuration" />
      <audiences>
        <audience>__AUDIENCE__</audience>
      </audiences>
      <issuers>
        <issuer>__ISSUER__</issuer>
      </issuers>
    </validate-jwt>
    <rate-limit calls="100" renewal-period="60" />
  </inbound>
</policies>
'''
var baseJwtValidationPolicy = replace(replace(baseJwtValidationPolicyRaw, '__ISSUER__', issuer), '__AUDIENCE__', apiAudience)

resource product 'Microsoft.ApiManagement/service/products@2023-05-01-preview' = {
  parent: apim
  name: 'novabank'
  properties: {
    displayName: 'NovaBank API'
    description: 'Customer, account, and card APIs behind a single gateway.'
    subscriptionRequired: false
    state: 'published'
  }
}

resource productPolicy 'Microsoft.ApiManagement/service/products/policies@2023-05-01-preview' = {
  parent: product
  name: 'policy'
  properties: {
    value: baseJwtValidationPolicy
    format: 'xml'
  }
}

resource customerApi 'Microsoft.ApiManagement/service/apis@2023-05-01-preview' = {
  parent: apim
  name: 'customer-api'
  properties: {
    displayName: 'Customer API'
    format: 'openapi-link'
    value: '${customerServiceUrl}/v3/api-docs'
    path: 'customer'
    protocols: ['https']
    serviceUrl: customerServiceUrl
    subscriptionRequired: false
  }
}

resource accountApi 'Microsoft.ApiManagement/service/apis@2023-05-01-preview' = {
  parent: apim
  name: 'account-api'
  properties: {
    displayName: 'Account API'
    format: 'openapi-link'
    value: '${accountServiceUrl}/v3/api-docs'
    path: 'account'
    protocols: ['https']
    serviceUrl: accountServiceUrl
    subscriptionRequired: false
  }
}

resource cardApi 'Microsoft.ApiManagement/service/apis@2023-05-01-preview' = {
  parent: apim
  name: 'card-api'
  properties: {
    displayName: 'Card API'
    format: 'openapi-link'
    value: '${cardServiceUrl}/v3/api-docs'
    path: 'card'
    protocols: ['https']
    serviceUrl: cardServiceUrl
    subscriptionRequired: false
  }
}

// account-api has exactly one POST operation (transfers) and the rest are GETs, so the
// required scope can be chosen purely by HTTP method — no need to address APIM's
// auto-generated per-operation resources (their names aren't known until after import).
//
// Uses an expression against the "jwt" variable set by the product-level policy, rather
// than <required-claims>, because Entra ID's "scp" claim is one space-delimited string
// (e.g. "accounts.read cards.read"), not a JSON array — required-claims does an exact-match
// comparison and would never match a single scope name against that whole string.
var accountApiPolicy = '''
<policies>
  <inbound>
    <base />
    <choose>
      <when condition="@{
          var jwt = context.Variables.GetValueOrDefault&lt;Jwt&gt;(&quot;jwt&quot;);
          var required = context.Request.Method == &quot;POST&quot; ? &quot;transfers.write&quot; : &quot;accounts.read&quot;;
          return !jwt.Claims.GetValueOrDefault(&quot;scp&quot;, new string[0]).Any(s =&gt; s.Split(' ').Contains(required));
      }">
        <return-response>
          <set-status code="403" reason="Forbidden" />
          <set-header name="Content-Type" exists-action="override">
            <value>application/json</value>
          </set-header>
          <set-body>{"statusCode":403,"message":"Missing required scope"}</set-body>
        </return-response>
      </when>
    </choose>
  </inbound>
</policies>
'''

resource accountApiPolicyRes 'Microsoft.ApiManagement/service/apis/policies@2023-05-01-preview' = {
  parent: accountApi
  name: 'policy'
  properties: {
    value: accountApiPolicy
    format: 'xml'
  }
}

// card-api's GET operations need cards.read; its POST operations (block/unblock) need
// cards.write — same expression-based approach and the same reason as account-api above.
var cardApiPolicy = '''
<policies>
  <inbound>
    <base />
    <choose>
      <when condition="@{
          var jwt = context.Variables.GetValueOrDefault&lt;Jwt&gt;(&quot;jwt&quot;);
          var required = context.Request.Method == &quot;POST&quot; ? &quot;cards.write&quot; : &quot;cards.read&quot;;
          return !jwt.Claims.GetValueOrDefault(&quot;scp&quot;, new string[0]).Any(s =&gt; s.Split(' ').Contains(required));
      }">
        <return-response>
          <set-status code="403" reason="Forbidden" />
          <set-header name="Content-Type" exists-action="override">
            <value>application/json</value>
          </set-header>
          <set-body>{"statusCode":403,"message":"Missing required scope"}</set-body>
        </return-response>
      </when>
    </choose>
  </inbound>
</policies>
'''

resource cardApiPolicyRes 'Microsoft.ApiManagement/service/apis/policies@2023-05-01-preview' = {
  parent: cardApi
  name: 'policy'
  properties: {
    value: cardApiPolicy
    format: 'xml'
  }
}

resource productCustomerApi 'Microsoft.ApiManagement/service/products/apis@2023-05-01-preview' = {
  parent: product
  name: customerApi.name
}

resource productAccountApi 'Microsoft.ApiManagement/service/products/apis@2023-05-01-preview' = {
  parent: product
  name: accountApi.name
}

resource productCardApi 'Microsoft.ApiManagement/service/products/apis@2023-05-01-preview' = {
  parent: product
  name: cardApi.name
}

output gatewayUrl string = apim.properties.gatewayUrl
