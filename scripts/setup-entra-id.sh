#!/usr/bin/env bash
# One-time setup: Entra ID app registration, scopes, app role, customerId extension claim,
# and two test users mapped to seeded customers. Run by the owner (Global Admin), not part
# of the Bicep deployment — app registrations live in Microsoft Graph, not ARM, so there's
# no native Bicep resource type for them on most Azure CLI/Bicep versions.
#
# This is a RECORD of the commands actually run to set up Stage 5, parameterized for
# re-use — it is not fully idempotent (re-running will create duplicate users/apps).
set -euo pipefail

DISPLAY_NAME="NovaBank API"
TENANT_DOMAIN="mibbrahim45gmail.onmicrosoft.com"
VAULT_NAME="nbdevkvzoyuqrptm2qia"

SCOPE_ACCOUNTS_READ=$(python3 -c "import uuid; print(uuid.uuid4())")
SCOPE_CARDS_READ=$(python3 -c "import uuid; print(uuid.uuid4())")
SCOPE_CARDS_WRITE=$(python3 -c "import uuid; print(uuid.uuid4())")
SCOPE_TRANSFERS_WRITE=$(python3 -c "import uuid; print(uuid.uuid4())")
ROLE_STAFF=$(python3 -c "import uuid; print(uuid.uuid4())")

echo "Creating app registration '$DISPLAY_NAME'..."
APP=$(az rest --method POST --url "https://graph.microsoft.com/v1.0/applications" --headers "Content-Type=application/json" --body "{
  \"displayName\": \"$DISPLAY_NAME\",
  \"signInAudience\": \"AzureADMyOrg\",
  \"api\": {
    \"requestedAccessTokenVersion\": 2,
    \"oauth2PermissionScopes\": [
      {\"id\": \"$SCOPE_ACCOUNTS_READ\", \"adminConsentDescription\": \"Read accounts\", \"adminConsentDisplayName\": \"Read accounts\", \"userConsentDescription\": \"Read your accounts\", \"userConsentDisplayName\": \"Read your accounts\", \"value\": \"accounts.read\", \"type\": \"User\", \"isEnabled\": true},
      {\"id\": \"$SCOPE_CARDS_READ\", \"adminConsentDescription\": \"Read cards\", \"adminConsentDisplayName\": \"Read cards\", \"userConsentDescription\": \"Read your cards\", \"userConsentDisplayName\": \"Read your cards\", \"value\": \"cards.read\", \"type\": \"User\", \"isEnabled\": true},
      {\"id\": \"$SCOPE_CARDS_WRITE\", \"adminConsentDescription\": \"Write cards\", \"adminConsentDisplayName\": \"Write cards\", \"userConsentDescription\": \"Manage your cards\", \"userConsentDisplayName\": \"Manage your cards\", \"value\": \"cards.write\", \"type\": \"User\", \"isEnabled\": true},
      {\"id\": \"$SCOPE_TRANSFERS_WRITE\", \"adminConsentDescription\": \"Write transfers\", \"adminConsentDisplayName\": \"Write transfers\", \"userConsentDescription\": \"Post transfers\", \"userConsentDisplayName\": \"Post transfers\", \"value\": \"transfers.write\", \"type\": \"User\", \"isEnabled\": true}
    ]
  },
  \"appRoles\": [
    {\"id\": \"$ROLE_STAFF\", \"allowedMemberTypes\": [\"User\"], \"description\": \"Back-office staff, bypasses the ownership check\", \"displayName\": \"NovaBank Staff\", \"value\": \"NovaBank.Staff\", \"isEnabled\": true}
  ]
}")
APP_ID=$(echo "$APP" | python3 -c "import json,sys; print(json.load(sys.stdin)['appId'])")
OBJECT_ID=$(echo "$APP" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")
echo "App ID: $APP_ID"

echo "Setting identifier URI..."
az rest --method PATCH --url "https://graph.microsoft.com/v1.0/applications/$OBJECT_ID" \
  --headers "Content-Type=application/json" \
  --body "{\"identifierUris\": [\"api://$APP_ID\"]}"

echo "Creating service principal..."
SP=$(az rest --method POST --url "https://graph.microsoft.com/v1.0/servicePrincipals" \
  --headers "Content-Type=application/json" --body "{\"appId\": \"$APP_ID\"}")
SP_ID=$(echo "$SP" | python3 -c "import json,sys; print(json.load(sys.stdin)['id'])")

echo "Creating customerId directory extension attribute..."
EXT=$(az rest --method POST --url "https://graph.microsoft.com/v1.0/applications/$OBJECT_ID/extensionProperties" \
  --headers "Content-Type=application/json" \
  --body '{"name": "customerId", "dataType": "String", "targetObjects": ["User"]}')
EXT_NAME=$(echo "$EXT" | python3 -c "import json,sys; print(json.load(sys.stdin)['name'])")
echo "Extension claim name: $EXT_NAME"

echo "Wiring customerId as an optional access-token claim..."
az rest --method PATCH --url "https://graph.microsoft.com/v1.0/applications/$OBJECT_ID" \
  --headers "Content-Type=application/json" \
  --body "{\"optionalClaims\": {\"accessToken\": [{\"name\": \"$EXT_NAME\", \"source\": null, \"essential\": false, \"additionalProperties\": []}]}}"

echo "Configuring the app as its own public test client (for ROPC token testing)..."
az rest --method PATCH --url "https://graph.microsoft.com/v1.0/applications/$OBJECT_ID" \
  --headers "Content-Type=application/json" \
  --body "{
    \"isFallbackPublicClient\": true,
    \"requiredResourceAccess\": [{
      \"resourceAppId\": \"$APP_ID\",
      \"resourceAccess\": [
        {\"id\": \"$SCOPE_ACCOUNTS_READ\", \"type\": \"Scope\"},
        {\"id\": \"$SCOPE_CARDS_READ\", \"type\": \"Scope\"},
        {\"id\": \"$SCOPE_CARDS_WRITE\", \"type\": \"Scope\"},
        {\"id\": \"$SCOPE_TRANSFERS_WRITE\", \"type\": \"Scope\"}
      ]
    }]
  }"

echo "Creating a second, minimal test-client app (accounts.read only)..."
echo "Needed because ROPC against the main app (acting as its own client) ignores a"
echo "narrower requested scope and returns the full admin-consented grant regardless —"
echo "this separate app genuinely cannot get transfers.write under any circumstance,"
echo "which the Stage 5 'missing scope returns 403' scenario needs to actually prove."
READONLY_APP=$(az rest --method POST --url "https://graph.microsoft.com/v1.0/applications" --headers "Content-Type=application/json" --body "{
  \"displayName\": \"NovaBank Test Client (read-only)\",
  \"signInAudience\": \"AzureADMyOrg\",
  \"isFallbackPublicClient\": true,
  \"requiredResourceAccess\": [{
    \"resourceAppId\": \"$APP_ID\",
    \"resourceAccess\": [{\"id\": \"$SCOPE_ACCOUNTS_READ\", \"type\": \"Scope\"}]
  }]
}")
READONLY_APP_ID=$(echo "$READONLY_APP" | python3 -c "import json,sys; print(json.load(sys.stdin)['appId'])")
az rest --method POST --url "https://graph.microsoft.com/v1.0/servicePrincipals" \
  --headers "Content-Type=application/json" --body "{\"appId\": \"$READONLY_APP_ID\"}" > /dev/null
echo "Read-only test client app ID: $READONLY_APP_ID"

echo ""
echo "Still needed (classifier blocks these for the assistant; run yourself):"
echo "  az ad app permission admin-consent --id $APP_ID"
echo "  az ad app permission admin-consent --id $READONLY_APP_ID"
echo ""
echo "Then create two test users, each mapped to a real seeded customerId:"
echo '  az rest --method POST --url "https://graph.microsoft.com/v1.0/users" --body "{...}"'
echo "(see architecture.md Stage 5 section for the exact body, or re-run this script's logic"
echo " with your own customer IDs)"
echo ""
echo "Finally, store each test user's password in Key Vault yourself:"
echo "  az keyvault secret set --vault-name $VAULT_NAME --name novabank-test1-password --value '<password>'"
echo "  az keyvault secret set --vault-name $VAULT_NAME --name novabank-test2-password --value '<password>'"
