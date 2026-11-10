package com.gabriel.financeflow.auth.service;

import com.gabriel.financeflow.shared.exception.TooManyRequestsException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String BLOCKED_MESSAGE = "Muitas tentativas de login. Tente novamente mais tarde.";

    private record Attempt(int failures, Instant windowStart, Instant lockedUntil) { }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public void checkNotBlocked(String email) {
        Instant now = Instant.now(clock);
        Attempt attempt = attempts.get(email);

        if (attempt == null || attempt.lockedUntil() == null) {
            return;
        }

        if (!now.isBefore(attempt.lockedUntil())) {
            attempts.remove(email);
            return;
        }

        Duration remaining = Duration.between(now, attempt.lockedUntil());
        throw new TooManyRequestsException(BLOCKED_MESSAGE, ceilSeconds(remaining));
    }

    private static long ceilSeconds(Duration duration) {
        return duration.toNanosPart() > 0 ? duration.toSeconds() + 1 : duration.toSeconds();
    }

    public void registerFailure(String email) {
        Instant now = Instant.now(clock);

        attempts.compute(email, (key, current) -> {
            if (current == null || !now.isBefore(current.windowStart().plus(WINDOW))) {
                return new Attempt(1, now, null);
            }

            int failures = current.failures() + 1;

            if (failures >= MAX_FAILURES) {
                return new Attempt(failures, current.windowStart(), now.plus(LOCK_DURATION));
            }

            return new Attempt(failures, current.windowStart(), null);
        });
    }

    public void registerSuccess(String email) {
        attempts.remove(email);
    }

    public void clear() {
        attempts.clear();
    }
}
