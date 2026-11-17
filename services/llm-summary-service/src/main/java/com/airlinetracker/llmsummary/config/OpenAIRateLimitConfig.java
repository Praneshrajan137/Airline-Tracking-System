package com.airlinetracker.llmsummary.config;

import com.airlinetracker.llmsummary.exception.OpenAIRateLimitUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/** Global Redis-backed OpenAI call limits. Redis failures stop provider requests. */
@Configuration
public class OpenAIRateLimitConfig {
    @Value("${openai.rate-limit.calls-per-minute:3}")
    private int callsPerMinute;
    @Value("${openai.rate-limit.calls-per-hour:10}")
    private int callsPerHour;
    @Value("${openai.rate-limit.calls-per-day:100}")
    private int callsPerDay;
    @Value("${openai.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Bean
    public RateLimiter openAIRateLimiter(StringRedisTemplate redisTemplate) {
        return new RateLimiter(redisTemplate, callsPerMinute, callsPerHour, callsPerDay, rateLimitEnabled);
    }

    public static class RateLimiter {
        private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
        private static final String KEY_PREFIX = "ratelimit:openai:";
        private static final String INCREMENT_WINDOW = "local n=redis.call('INCR',KEYS[1]); " +
                "if n == 1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return n";
        private static final DefaultRedisScript<Long> SCRIPT =
                new DefaultRedisScript<>(INCREMENT_WINDOW, Long.class);

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
            return within("minute", 60, perMinute)
                    && within("hour", 3_600, perHour)
                    && within("day", 86_400, perDay);
        }

        private boolean within(String window, int seconds, int limit) {
            try {
                Long count = redis.execute(SCRIPT, List.of(KEY_PREFIX + window), Integer.toString(seconds));
                if (count == null) throw new OpenAIRateLimitUnavailableException("OpenAI quota counter returned no result");
                if (count > limit) {
                    log.warn("OpenAI call quota exceeded for {} window ({}/{})", window, count, limit);
                    return false;
                }
                return true;
            } catch (OpenAIRateLimitUnavailableException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("OpenAI quota storage unavailable; rejecting provider request", exception);
                throw new OpenAIRateLimitUnavailableException("OpenAI quota storage is unavailable", exception);
            }
        }
    }
}
