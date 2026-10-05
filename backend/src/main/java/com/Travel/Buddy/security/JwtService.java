package com.Travel.Buddy.security;

import com.Travel.Buddy.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;


    // ============================================================
    // SECRET KEY
    // ============================================================

    private SecretKey getSigningKey() {

        return Keys.hmacShaKeyFor(
                jwtSecret.getBytes(StandardCharsets.UTF_8)
        );
    }


    // ============================================================
    // GENERATE TOKEN FROM EMAIL
    // ============================================================

    public String generateToken(String email) {

        Date now = new Date();

        Date expiration =
                new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey())
                .compact();
    }


    // ============================================================
    // GENERATE TOKEN FROM SPRING USER DETAILS
    // ============================================================

    public String generateToken(UserDetails userDetails) {

        return generateToken(userDetails.getUsername());
    }


    // ============================================================
    // GENERATE TOKEN FROM TRAVEL BUDDY USER ENTITY
    // ============================================================

    public String generateToken(User user) {

        return generateToken(user.getEmail());
    }


    // ============================================================
    // GET JWT EXPIRATION
    // ============================================================

    public long getExpirationMs() {

        return jwtExpirationMs;
    }


    // ============================================================
    // EXTRACT USERNAME / EMAIL
    // ============================================================

    public String extractUsername(String token) {

        return extractClaim(
                token,
                Claims::getSubject
        );
    }


    // ============================================================
    // GENERIC CLAIM EXTRACTION
    // ============================================================

    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {

        Claims claims = extractAllClaims(token);

        return claimsResolver.apply(claims);
    }


    // ============================================================
    // EXTRACT ALL CLAIMS
    // ============================================================

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    // ============================================================
    // TOKEN VALIDATION
    // ============================================================

    public boolean isTokenValid(String token) {

        try {

            extractAllClaims(token);

            return !isTokenExpired(token);

        } catch (Exception exception) {

            return false;
        }
    }


    // ============================================================
    // TOKEN VALIDATION WITH USER DETAILS
    // ============================================================

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {

        try {

            String username =
                    extractUsername(token);

            return username != null
                    && username.equals(userDetails.getUsername())
                    && !isTokenExpired(token);

        } catch (Exception exception) {

            return false;
        }
    }


    // ============================================================
    // CHECK EXPIRATION
    // ============================================================

    private boolean isTokenExpired(String token) {

        Date expiration =
                extractClaim(token, Claims::getExpiration);

        return expiration.before(new Date());
    }
}