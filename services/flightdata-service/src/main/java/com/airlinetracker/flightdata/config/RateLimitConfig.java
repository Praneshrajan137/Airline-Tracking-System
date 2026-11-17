package com.airlinetracker.flightdata.config;

import com.airlinetracker.flightdata.exception.RateLimitUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/** Redis-backed global provider quota; Redis failures reject requests to protect the API key. */
@Configuration
public class RateLimitConfig {
    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    private static final String INCREMENT_WINDOW = "local n=redis.call('INCR',KEYS[1]); " +
            "if n == 1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n";

    @Value("${flightaware.rate-limit.calls-per-minute:10}")
    private int callsPerMinute;
    @Value("${flightaware.rate-limit.calls-per-hour:200}")
    private int callsPerHour;
    @Value("${flightaware.rate-limit.calls-per-day:300}")
    private int callsPerDay;
    @Value("${flightaware.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public RateLimiter flightAwareRateLimiter(StringRedisTemplate redisTemplate) {
        return new RateLimiter(redisTemplate, callsPerMinute, callsPerHour, callsPerDay, rateLimitEnabled);
    }

    public static class RateLimiter {
        private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
        private static final String KEY_PREFIX = "ratelimit:flightaware:";
        private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(INCREMENT_WINDOW, Long.class);
        private final StringRedisTemplate redis;
        private final int perMinute;
        private final int perHour;
        private final int perDay;
        private final boolean enabled;

        public RateLimiter(StringRedisTemplate redis, int perMinute, int perHour, int perDay, boolean enabled) {
            this.redis = redis;
            this.perMinute = perMinute;
            this.perHour = perHour;
            this.perDay = perDay;
            this.enabled = enabled;
        }

        public boolean allowRequest() {
            if (!enabled) return true;
            return checkLimit("minute", 60, perMinute)
                    && checkLimit("hour", 3_600, perHour)
                    && checkLimit("day", 86_400, perDay);
        }

        private boolean checkLimit(String window, int seconds, int limit) {
            try {
                Long count = redis.execute(INCREMENT_SCRIPT,
                        List.of(KEY_PREFIX + window), Integer.toString(seconds));
                if (count == null) {
                    log.error("Provider quota counter returned no result; rejecting outbound request");
                    throw new RateLimitUnavailableException("Provider quota storage returned no result");
                }
                if (count > limit) {
                    log.warn("Provider quota exceeded for {} window ({}/{})", window, count, limit);
                    return false;
                }
                return true;
            } catch (Exception exception) {
                log.error("Provider quota store unavailable; rejecting outbound request", exception);
                throw new RateLimitUnavailableException("Provider quota storage is unavailable", exception);
            }
        }

        public UsageStats getUsageStats() {
            try {
                return new UsageStats(read("minute"), perMinute, read("hour"), perHour, read("day"), perDay);
            } catch (Exception exception) {
                log.warn("Unable to read provider quota counters", exception);
                return new UsageStats(0, perMinute, 0, perHour, 0, perDay);
            }
        }

        private int read(String window) {
            String value = redis.opsForValue().get(KEY_PREFIX + window);
            return value == null ? 0 : Integer.parseInt(value);
        }
    }

    public static class UsageStats {
        public final int minuteUsed;
        public final int minuteLimit;
        public final int hourUsed;
        public final int hourLimit;
        public final int dayUsed;
        public final int dayLimit;

        public UsageStats(int minuteUsed, int minuteLimit, int hourUsed, int hourLimit, int dayUsed, int dayLimit) {
            this.minuteUsed = minuteUsed;
            this.minuteLimit = minuteLimit;
            this.hourUsed = hourUsed;
            this.hourLimit = hourLimit;
            this.dayUsed = dayUsed;
            this.dayLimit = dayLimit;
        }

        @Override
        public String toString() {
            return String.format("minute=%d/%d, hour=%d/%d, day=%d/%d",
                    minuteUsed, minuteLimit, hourUsed, hourLimit, dayUsed, dayLimit);
        }
    }
}
