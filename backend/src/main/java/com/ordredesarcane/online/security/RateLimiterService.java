package com.ordredesarcane.online.security;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

    public static final int MAX_GAME_ACTIONS_PER_SECOND = 5;
    public static final int MAX_CHAT_MESSAGES_PER_SECOND = 1;
    private static final long WINDOW_MILLIS = 1_000L;

    private final Clock clock;
    private final ConcurrentHashMap<String, SlidingWindow> buckets = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiterService() {
        this(Clock.systemUTC());
    }

    public RateLimiterService(Clock clock) {
        this.clock = clock;
    }

    public boolean allowGameAction(String playerId) {
        return allow("game:" + playerId, MAX_GAME_ACTIONS_PER_SECOND);
    }

    public boolean allowChatMessage(String playerId) {
        return allow("chat:" + playerId, MAX_CHAT_MESSAGES_PER_SECOND);
    }

    private boolean allow(String key, int maxRequestsPerSecond) {
        long now = clock.millis();
        SlidingWindow window = buckets.computeIfAbsent(key, ignored -> new SlidingWindow());
        return window.tryAcquire(now, maxRequestsPerSecond);
    }

    private static final class SlidingWindow {
        private final Deque<Long> timestamps = new ArrayDeque<>();

        synchronized boolean tryAcquire(long nowMillis, int limit) {
            long cutoff = nowMillis - WINDOW_MILLIS;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= limit) {
                return false;
            }
            timestamps.addLast(nowMillis);
            return true;
        }
    }
}
