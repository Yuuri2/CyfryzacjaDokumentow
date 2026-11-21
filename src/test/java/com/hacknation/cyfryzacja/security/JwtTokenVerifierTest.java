package com.hacknation.cyfryzacja.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenVerifierTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final String ISSUER = "formforge";

    private final JwtTokenVerifier verifier =
            new JwtTokenVerifier(new JwtProperties(SECRET, ISSUER, 3600));

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String secret, String issuer, Instant expires) {
        return Jwts.builder()
                .issuer(issuer)
                .subject("jury")
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expires))
                .signWith(key(secret), Jwts.SIG.HS256)
                .compact();
    }

    @Test
    void acceptsValidToken() {
        Optional<String> subject = verifier.verify(token(SECRET, ISSUER, Instant.now().plusSeconds(60)));
        assertEquals(Optional.of("jury"), subject);
    }

    @Test
    void rejectsWrongSignature() {
        String forged = token("another-secret-another-secret-12345", ISSUER, Instant.now().plusSeconds(60));
        assertTrue(verifier.verify(forged).isEmpty());
    }

    @Test
    void rejectsExpiredToken() {
        assertTrue(verifier.verify(token(SECRET, ISSUER, Instant.now().minusSeconds(5))).isEmpty());
    }

    @Test
    void rejectsWrongIssuer() {
        assertTrue(verifier.verify(token(SECRET, "someone-else", Instant.now().plusSeconds(60))).isEmpty());
    }

    @Test
    void rejectsAlgNone() {
        String none = Jwts.builder().issuer(ISSUER).subject("jury")
                .expiration(Date.from(Instant.now().plusSeconds(60))).compact();
        assertTrue(verifier.verify(none).isEmpty());
    }

    @Test
    void rejectsOtherHmacAlgorithm() {
        String hs512 = Jwts.builder().issuer(ISSUER).subject("jury")
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor((SECRET + SECRET).getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS512)
                .compact();
        assertTrue(verifier.verify(hs512).isEmpty());
    }

    @Test
    void rejectsGarbage() {
        assertTrue(verifier.verify("not-a-jwt").isEmpty());
        assertTrue(verifier.verify("").isEmpty());
    }
}
