package com.ordredesarcane.online.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.ordredesarcane.online.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final Clock clock;

    @Autowired
    public JwtService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] keyBytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        try {
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
        } catch (JOSEException ex) {
            throw new IllegalStateException("Impossible d'initialiser la clé JWT HS512", ex);
        }
    }

    public String issueToken(PlayerPrincipal principal) {
        Instant now = clock.instant();
        Instant expiration = now.plusSeconds(properties.jwtExpirationSeconds());

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(principal.userId().toString())
                .claim(CLAIM_USERNAME, principal.username())
                .claim(CLAIM_ROLE, principal.role())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiration))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
        try {
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException ex) {
            throw new IllegalStateException("Erreur de signature JWT HS512", ex);
        }
    }

    public Optional<PlayerPrincipal> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS512.equals(signedJwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            if (!signedJwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            Date exp = claims.getExpirationTime();
            if (exp == null || !exp.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            String username = claims.getStringClaim(CLAIM_USERNAME);
            String role = claims.getStringClaim(CLAIM_ROLE);
            if (username == null || username.isBlank() || role == null || role.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new PlayerPrincipal(userId, username, role));
        } catch (ParseException | JOSEException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public ResponseCookie createSessionCookie(String token) {
        return ResponseCookie.from(properties.cookieName(), token)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(properties.jwtExpirationSeconds())
                .build();
    }

    public ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(properties.cookieName(), "")
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
    }
}
