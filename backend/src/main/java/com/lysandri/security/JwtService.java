package com.lysandri.security;

import com.lysandri.adapters.out.database.entity.UsuarioEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof UsuarioEntity usuario) {
            extraClaims.put("id", usuario.getIdUser());
            extraClaims.put("email", usuario.getEmail());
            if (usuario.getRol() != null) {
                extraClaims.put("rol", usuario.getRol().name());
            }
        }
        return generateToken(extraClaims, userDetails);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public record GuestSessionClaims(String sessionId, String email) {}

    /**
     * Emite un token criptográfico HMAC-SHA256 para aislar y autorizar de forma segura
     * las operaciones de checkout y recuperación de órdenes de invitados.
     */
    public String generateGuestCheckoutToken(String guestSessionId, String email) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("type", "GUEST_CHECKOUT_SESSION");
        extraClaims.put("sessionId", guestSessionId);
        extraClaims.put("email", email != null && !email.isBlank() ? email.trim().toLowerCase() : "");
        long guestExpiration = 86_400_000L; // 24 horas
        return Jwts.builder()
                .claims(extraClaims)
                .subject("guest:" + (email != null && !email.isBlank() ? email.trim().toLowerCase() : "anonymous"))
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + guestExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    /**
     * Valida el token de sesión de invitado verificando su firma HMAC y expiración.
     * Retorna los claims extraídos o null si la firma o estructura es inválida.
     */
    public GuestSessionClaims validateGuestCheckoutToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = extractAllClaims(token);
            if (!"GUEST_CHECKOUT_SESSION".equals(claims.get("type"))) {
                return null;
            }
            if (claims.getExpiration().before(new Date())) {
                return null;
            }
            String sessionId = (String) claims.get("sessionId");
            String email = (String) claims.get("email");
            if (sessionId == null || sessionId.isBlank()) {
                return null;
            }
            return new GuestSessionClaims(sessionId, email);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
