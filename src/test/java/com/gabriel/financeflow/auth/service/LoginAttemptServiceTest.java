package com.gabriel.financeflow.auth.service;

import com.gabriel.financeflow.shared.exception.TooManyRequestsException;
import com.gabriel.financeflow.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "gabriel@email.com";

    private MutableClock clock;
    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        service = new LoginAttemptService(clock);
    }

    @Test
    void fourFailuresDoNotBlock() {
        fail(EMAIL, 4);

        assertThatCode(() -> service.checkNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void fiveFailuresBlock() {
        fail(EMAIL, 5);

        assertThatThrownBy(() -> service.checkNotBlocked(EMAIL))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("Muitas tentativas");
    }

    @Test
    void retryAfterCountsDownTheRemainingSeconds() {
        fail(EMAIL, 5);
        assertRetryAfter(900);

        clock.advance(Duration.ofMinutes(10));
        assertRetryAfter(300);
    }

    @Test
    void retryAfterRoundsUpFractionsOfSecond() {
        fail(EMAIL, 5);

        // Faltam 899,7 s: o cliente precisa esperar 900, e não 899
        clock.advance(Duration.ofMillis(300));
        assertRetryAfter(900);
    }

    @Test
    void blockEndsAfter15Minutes() {
        fail(EMAIL, 5);

        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> service.checkNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void afterBlockEndsTheCounterStartsOver() {
        fail(EMAIL, 5);
        clock.advance(Duration.ofMinutes(15));
        service.checkNotBlocked(EMAIL);

        // Uma falha nova não bloqueia de novo: o contador recomeçou
        fail(EMAIL, 1);

        assertThatCode(() -> service.checkNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void successResetsTheCounter() {
        fail(EMAIL, 4);
        service.registerSuccess(EMAIL);
        fail(EMAIL, 4);

        // Seriam 8 falhas sem o reset
        assertThatCode(() -> service.checkNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindowDoNotAccumulate() {
        fail(EMAIL, 4);

        clock.advance(Duration.ofMinutes(16));
        fail(EMAIL, 1);

        assertThatCode(() -> service.checkNotBlocked(EMAIL)).doesNotThrowAnyException();
    }

    @Test
    void emailsAreIndependent() {
        fail("a@email.com", 5);

        assertThatThrownBy(() -> service.checkNotBlocked("a@email.com"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkNotBlocked("b@email.com")).doesNotThrowAnyException();
    }

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            service.registerFailure(email);
        }
    }

    private void assertRetryAfter(long expectedSeconds) {
        assertThatThrownBy(() -> service.checkNotBlocked(EMAIL))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.getRetryAfterSeconds()).isEqualTo(expectedSeconds));
    }
}
