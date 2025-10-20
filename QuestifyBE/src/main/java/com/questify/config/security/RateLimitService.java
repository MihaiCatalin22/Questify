package com.questify.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final RedisConnectionFactory redis;

    public void checkOrThrow(String key, long limit, Duration window) {
        var bytesKey = key.getBytes(StandardCharsets.UTF_8);
        try (var conn = redis.getConnection()) {
            Long count = conn.stringCommands().incr(bytesKey);
            if (count != null && count == 1L) {
                conn.keyCommands().expire(bytesKey, window);
            }
            if (count != null && count > limit) {
                throw new TooManyRequestsException("Rate limit exceeded");
            }
        }
    }

    public static class TooManyRequestsException extends RuntimeException {
        public TooManyRequestsException(String msg) { super(msg); }
    }
}
