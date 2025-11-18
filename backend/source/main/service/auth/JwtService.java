package service.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;

@Component
@Slf4j
public class JwtService {

    @Value("${jwt.signerKey}")
    private String signerKey;

    public String generateToken(String username, Set<String> roles) {
        return Jwts.builder()
                .setSubject(username)
                .claim("roles", roles)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()
                ))
                .signWith(getSignInKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String generateRefreshToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(
                        Instant.now().plus(30, ChronoUnit.DAYS).toEpochMilli()
                ))
                .signWith(getSignInKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractUsername(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        Date expiration = extractClaims(token).getExpiration();
        return expiration.before(new Date());
    }

    private Key getSignInKey() {
        // Support both Base64-encoded and plain-text secrets for compatibility
        try {
            byte[] decoded = java.util.Base64.getDecoder().decode(signerKey);
            if (decoded != null && decoded.length > 0) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Not Base64, fall back to plain string bytes
        }
        return Keys.hmacShaKeyFor(signerKey.getBytes());
    }
}
