package com.airlinetracker.flightdata.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Rate Limiting Configuration to protect FlightAware API free tier
 * Prevents API key misuse and cost overruns
 */
@Configuration
public class RateLimitConfig {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);
    
    @Value("${flightaware.rate-limit.calls-per-minute:10}")
    private int callsPerMinute;
    
    @Value("${flightaware.rate-limit.calls-per-hour:200}")
    private int callsPerHour;
    
    @Value("${flightaware.rate-limit.calls-per-day:300}")
    private int callsPerDay;
    
    @Value("${flightaware.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;
    
    @Bean
    public RateLimiter flightAwareRateLimiter(RedisTemplate<String, String> redisTemplate) {
        log.info("Initializing FlightAware API Rate Limiter:");
        log.info("  - Calls/minute: {}", callsPerMinute);
        log.info("  - Calls/hour: {}", callsPerHour);
        log.info("  - Calls/day: {}", callsPerDay);
        log.info("  - Enabled: {}", rateLimitEnabled);
        
        return new RateLimiter(
            redisTemplate,
            callsPerMinute,
            callsPerHour,
            callsPerDay,
            rateLimitEnabled
        );
    }
    
    /**
     * Redis-backed distributed rate limiter
     */
    public static class RateLimiter {
        private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
        private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:flightaware:";
        
        private final RedisTemplate<String, String> redisTemplate;
        private final int callsPerMinute;
        private final int callsPerHour;
        private final int callsPerDay;
        private final boolean enabled;
        
        public RateLimiter(RedisTemplate<String, String> redisTemplate,
                          int callsPerMinute, int callsPerHour, int callsPerDay,
                          boolean enabled) {
            this.redisTemplate = redisTemplate;
            this.callsPerMinute = callsPerMinute;
            this.callsPerHour = callsPerHour;
            this.callsPerDay = callsPerDay;
            this.enabled = enabled;
        }
        
        /**
         * Check if API call is allowed under rate limits
         * @return true if allowed, false if rate limit exceeded
         */
        public boolean allowRequest() {
            if (!enabled) {
                return true;
            }
            
            // Check minute limit
            if (!checkLimit("minute", 60, callsPerMinute)) {
                log.warn("⚠️ Rate limit exceeded: {} calls/minute", callsPerMinute);
                return false;
            }
            
            // Check hour limit
            if (!checkLimit("hour", 3600, callsPerHour)) {
                log.warn("⚠️ Rate limit exceeded: {} calls/hour", callsPerHour);
                return false;
            }
            
            // Check day limit
            if (!checkLimit("day", 86400, callsPerDay)) {
                log.warn("⚠️ Rate limit exceeded: {} calls/day", callsPerDay);
                return false;
            }
            
            return true;
        }
        
        /**
         * Check and increment rate limit counter
         */
        private boolean checkLimit(String window, int windowSeconds, int maxCalls) {
            String key = RATE_LIMIT_KEY_PREFIX + window;
            
            try {
                Long currentCount = redisTemplate.opsForValue().increment(key);
                
                if (currentCount == null) {
                    currentCount = 0L;
                }
                
                // Set expiry on first increment
                if (currentCount == 1) {
                    redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
                }
                
                if (currentCount > maxCalls) {
                    log.warn("Rate limit check: {}/{} calls in {} window", 
                            currentCount, maxCalls, window);
                    return false;
                }
                
                // Log usage at warning thresholds
                if (currentCount >= maxCalls * 0.8) {
                    log.warn("⚠️ API usage at {}%: {}/{} calls in {} window", 
                            (int)((currentCount * 100.0) / maxCalls),
                            currentCount, maxCalls, window);
                }
                
                return true;
                
            } catch (Exception e) {
                log.error("Rate limit check failed, allowing request: {}", e.getMessage());
                return true; // Fail open
            }
        }
        
        /**
         * Get current usage statistics
         */
        public UsageStats getUsageStats() {
            try {
                String minuteCount = redisTemplate.opsForValue().get(RATE_LIMIT_KEY_PREFIX + "minute");
                String hourCount = redisTemplate.opsForValue().get(RATE_LIMIT_KEY_PREFIX + "hour");
                String dayCount = redisTemplate.opsForValue().get(RATE_LIMIT_KEY_PREFIX + "day");
                
                return new UsageStats(
                    parseInt(minuteCount), callsPerMinute,
                    parseInt(hourCount), callsPerHour,
                    parseInt(dayCount), callsPerDay
                );
            } catch (Exception e) {
                log.error("Failed to get usage stats: {}", e.getMessage());
                return new UsageStats(0, callsPerMinute, 0, callsPerHour, 0, callsPerDay);
            }
        }
        
        private int parseInt(String value) {
            return value != null ? Integer.parseInt(value) : 0;
        }
    }
    
    /**
     * Usage statistics for monitoring
     */
    public static class UsageStats {
        public final int minuteUsed;
        public final int minuteLimit;
        public final int hourUsed;
        public final int hourLimit;
        public final int dayUsed;
        public final int dayLimit;
        
        public UsageStats(int minuteUsed, int minuteLimit,
                         int hourUsed, int hourLimit,
                         int dayUsed, int dayLimit) {
            this.minuteUsed = minuteUsed;
            this.minuteLimit = minuteLimit;
            this.hourUsed = hourUsed;
            this.hourLimit = hourLimit;
            this.dayUsed = dayUsed;
            this.dayLimit = dayLimit;
        }
        
        @Override
        public String toString() {
            return String.format("Usage: minute=%d/%d, hour=%d/%d, day=%d/%d",
                    minuteUsed, minuteLimit, hourUsed, hourLimit, dayUsed, dayLimit);
        }
    }
}

