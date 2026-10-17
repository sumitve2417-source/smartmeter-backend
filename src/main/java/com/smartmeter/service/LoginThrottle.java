package com.smartmeter.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/** Locks a username for 15 minutes after 5 failed logins. In-memory: move to Redis if you run several instances. */
@Component
public class LoginThrottle {
    private static final int MAX_FAILS = 5;
    private static final Duration LOCK = Duration.ofMinutes(15);

    private record State(int fails, Instant lockedUntil) {}
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        State s = states.get(username.toLowerCase());
        return s != null && s.lockedUntil() != null && Instant.now().isBefore(s.lockedUntil());
    }

    public void recordFailure(String username) {
        states.merge(username.toLowerCase(), new State(1, null), (old, ignore) -> {
            int n = old.lockedUntil() != null && Instant.now().isAfter(old.lockedUntil()) ? 1 : old.fails() + 1;
            return new State(n, n >= MAX_FAILS ? Instant.now().plus(LOCK) : null);
        });
    }

    public void recordSuccess(String username) {
        states.remove(username.toLowerCase());
    }
}
