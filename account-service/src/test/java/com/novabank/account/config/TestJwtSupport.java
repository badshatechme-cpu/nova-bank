package com.novabank.account.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Mints real, RSA-signed test JWTs so integration tests exercise the actual
 * SecurityFilterChain/JwtDecoder/OwnershipCheckFilter over real HTTP, rather than mocking
 * them away. TestSecurityConfig's JwtDecoder validates signatures against this same key pair.
 */
public final class TestJwtSupport {

    public static final String EXPECTED_AUDIENCE = "bf3edf1a-2e04-4794-a3d3-5e516c5321ef";
    public static final String CUSTOMER_ID_CLAIM = "extn.customerId";
    public static final String STAFF_ROLE = "NovaBank.Staff";

    public static final KeyPair KEY_PAIR = generateKeyPair();

    private TestJwtSupport() {
    }

    public static RSAPublicKey publicKey() {
        return (RSAPublicKey) KEY_PAIR.getPublic();
    }

    public static String tokenFor(String customerId) {
        return token(customerId, null);
    }

    public static String staffToken() {
        return token(null, List.of(STAFF_ROLE));
    }

    public static String token(String customerId, List<String> roles) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject("test-user")
                    .issuer("https://login.microsoftonline.com/357b06c3-23ee-408a-a70b-57eaddc48aad/v2.0")
                    .audience(EXPECTED_AUDIENCE)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)));
            if (customerId != null) {
                // Real Entra ID tokens carry this as a single-element array, not a plain string.
                claims.claim(CUSTOMER_ID_CLAIM, List.of(customerId));
            }
            if (roles != null) {
                claims.claim("roles", roles);
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
            jwt.sign(new RSASSASigner((RSAPrivateKey) KEY_PAIR.getPrivate()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
