package com.chessinsights.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.AlgorithmParameters;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.jsonwebtoken.*;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final String supabaseUrl;
    private final Map<String, ECPublicKey> keyCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(@Value("${app.supabase.url}") String supabaseUrl) {
        this.supabaseUrl = supabaseUrl;
        log.info("JwtAuthFilter initialized with Supabase URL: {}", supabaseUrl);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.info("JWT token received for {} {}", request.getMethod(), request.getRequestURI());

            try {
                String[] parts = token.split("\\.");
                if (parts.length == 3) {
                    String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]));
                    JsonNode header = objectMapper.readTree(headerJson);
                    String kid = header.has("kid") ? header.get("kid").asText() : null;
                    String alg = header.has("alg") ? header.get("alg").asText() : null;

                    log.info("Token algorithm: {}, kid: {}", alg, kid);

                    if ("ES256".equals(alg) && kid != null) {
                        ECPublicKey publicKey = getPublicKey(kid);
                        if (publicKey != null) {
                            log.info("Public key found for kid: {}", kid);

                            Claims claims = Jwts.parser()
                                    .verifyWith(publicKey)
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();

                            String supabaseUserId = claims.getSubject();
                            log.info("Token validated successfully. User ID: {}", supabaseUserId);

                            if (supabaseUserId != null) {
                                UsernamePasswordAuthenticationToken auth =
                                        new UsernamePasswordAuthenticationToken(
                                                supabaseUserId, null, Collections.emptyList());
                                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                log.info("Authentication set for user: {}", supabaseUserId);
                            }
                        } else {
                            log.warn("Could not find public key for kid: {}", kid);
                        }
                    } else {
                        log.warn("Unsupported token algorithm: {} or missing kid", alg);
                    }
                } else {
                    log.warn("Invalid token format - expected 3 parts, got {}", parts.length);
                }
            } catch (JwtException e) {
                log.warn("JWT validation failed: {}", e.getMessage());
            } catch (Exception e) {
                log.error("Error processing JWT: {}", e.getMessage(), e);
            }
        }

        filterChain.doFilter(request, response);
    }

    private ECPublicKey getPublicKey(String kid) {
        return keyCache.computeIfAbsent(kid, k -> {
            try {
                String jwksUrl = supabaseUrl + "/auth/v1/.well-known/jwks.json";
                log.info("Fetching JWKS from: {}", jwksUrl);

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(jwksUrl))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                log.info("JWKS response status: {}, body length: {}", resp.statusCode(), resp.body().length());

                JsonNode jwks = objectMapper.readTree(resp.body());
                JsonNode keys = jwks.get("keys");

                if (keys != null && keys.isArray()) {
                    log.info("Found {} keys in JWKS", keys.size());
                    for (JsonNode key : keys) {
                        String keyKid = key.get("kid").asText();
                        log.info("Checking key with kid: {}", keyKid);
                        if (kid.equals(keyKid)) {
                            log.info("Matched key for kid: {}", kid);
                            return buildECPublicKey(key);
                        }
                    }
                    log.warn("No matching key found for kid: {}", kid);
                } else {
                    log.warn("No keys array in JWKS response");
                }
            } catch (Exception e) {
                log.error("Failed to fetch JWKS: {}", e.getMessage(), e);
            }
            return null;
        });
    }

    private ECPublicKey buildECPublicKey(JsonNode jwk) throws Exception {
        byte[] xBytes = Base64.getUrlDecoder().decode(jwk.get("x").asText());
        byte[] yBytes = Base64.getUrlDecoder().decode(jwk.get("y").asText());

        BigInteger x = new BigInteger(1, xBytes);
        BigInteger y = new BigInteger(1, yBytes);

        ECPoint point = new ECPoint(x, y);

        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec ecSpec = parameters.getParameterSpec(ECParameterSpec.class);

        ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, ecSpec);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return (ECPublicKey) keyFactory.generatePublic(pubKeySpec);
    }
}