package com.gabriel.financeflow.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtEncodingException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class JwtConfigTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void propertiesAreBoundFromYaml() {
        assertThat(jwtProperties.secret()).isNotBlank();
        assertThat(jwtProperties.accessTokenMinutes()).isEqualTo(15);
        assertThat(jwtProperties.refreshTokenDays()).isEqualTo(7);
    }

    @Test
    void tokenEncodedWithHs256IsDecodedBack() {
        String token = encode(JwsHeader.with(MacAlgorithm.HS256).build(), Instant.now().plus(15, ChronoUnit.MINUTES));

        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo("user-id");
        assertThat(decoded.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void encodingWithoutHs256HeaderFails() {
        // A "pegadinha": sem o header, o Nimbus procura uma chave RS256 e não encontra.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user-id")
                .expiresAt(Instant.now().plus(15, ChronoUnit.MINUTES))
                .build();

        assertThatThrownBy(() -> jwtEncoder.encode(JwtEncoderParameters.from(claims)))
                .isInstanceOf(JwtEncodingException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        String token = encode(JwsHeader.with(MacAlgorithm.HS256).build(), Instant.now().minus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = encode(JwsHeader.with(MacAlgorithm.HS256).build(), Instant.now().plus(15, ChronoUnit.MINUTES));
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        assertThatThrownBy(() -> jwtDecoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void passwordEncoderUsesBcrypt() {
        String hash = passwordEncoder.encode("senha-segura");

        assertThat(hash).startsWith("$2").isNotEqualTo("senha-segura");
        assertThat(passwordEncoder.matches("senha-segura", hash)).isTrue();
        assertThat(passwordEncoder.matches("senha-errada", hash)).isFalse();
    }

    private String encode(JwsHeader header, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("user-id")
                .issuedAt(expiresAt.minus(15, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
