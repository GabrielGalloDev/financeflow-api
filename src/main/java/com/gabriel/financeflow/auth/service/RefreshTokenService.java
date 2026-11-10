package com.gabriel.financeflow.auth.service;

import com.gabriel.financeflow.auth.entity.RefreshToken;
import com.gabriel.financeflow.auth.repository.RefreshTokenRepository;
import com.gabriel.financeflow.shared.exception.InvalidRefreshTokenException;
import com.gabriel.financeflow.shared.security.JwtProperties;
import com.gabriel.financeflow.user.entity.User;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN = "Refresh token inválido ou expirado";

    private final SecureRandom secureRandom = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties, Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** Cria um refresh token para o usuário e devolve o texto ORIGINAL (só o hash vai para o banco). */
    public String issue(User user) {
        String rawToken = generateRandomToken();
        Instant expiresAt = Instant.now(clock).plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS);

        refreshTokenRepository.save(new RefreshToken(user, hash(rawToken), expiresAt));

        return rawToken;
    }

    /** Valida, revoga (rotação) e devolve o dono do token. */
    public User consume(String rawToken) {
        Instant now = Instant.now(clock);

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_TOKEN));

        if (!token.isActive(now)) {
            throw new InvalidRefreshTokenException(INVALID_TOKEN);
        }

        token.revoke(now);
        return token.getUser();
    }

    /** Logout: revoga se existir e estiver ativo. Nunca lança erro. */
    public void revoke(String rawToken) {
        Instant now = Instant.now(clock);

        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.isActive(now))
                .ifPresent(token -> token.revoke(now));
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // Todo Java é obrigado a ter SHA-256: isto nunca acontece
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
