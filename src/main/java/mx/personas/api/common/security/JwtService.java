package mx.personas.api.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import mx.personas.api.usuario.model.Rol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Firma y valida los tokens de acceso JWT (HS256), y genera/hashea el valor opaco del
 * token de refresco. Ver research.md #1-3.
 */
@Component
public class JwtService {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey llave;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();

    public JwtService(@Value("${app.security.jwt-secret}") String secreto,
                       @Value("${app.security.access-token-ttl-minutes}") long accessTokenTtlMinutes,
                       @Value("${app.security.refresh-token-ttl-days}") long refreshTokenTtlDays) {
        this.llave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
        this.refreshTokenTtl = Duration.ofDays(refreshTokenTtlDays);
    }

    public String generarAccessToken(String login, Rol rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(login)
                .claim(CLAIM_ROL, rol.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(accessTokenTtl)))
                .signWith(llave)
                .compact();
    }

    public Instant expiracionAccessToken() {
        return Instant.now().plus(accessTokenTtl);
    }

    public Instant expiracionRefreshToken() {
        return Instant.now().plus(refreshTokenTtl);
    }

    /**
     * Parsea y valida el token; lanza JwtException (firma invalida, expirado, malformado)
     * si no es valido. El llamador (JwtAuthenticationFilter) decide como reaccionar.
     */
    public Claims parsearYValidar(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(llave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extraerLogin(Claims claims) {
        return claims.getSubject();
    }

    public Rol extraerRol(Claims claims) {
        return Rol.valueOf(claims.get(CLAIM_ROL, String.class));
    }

    /** Valor opaco de alta entropia para el token de refresco (no es un JWT - research.md #3). */
    public String generarTokenOpacoRefresh() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashSha256(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
