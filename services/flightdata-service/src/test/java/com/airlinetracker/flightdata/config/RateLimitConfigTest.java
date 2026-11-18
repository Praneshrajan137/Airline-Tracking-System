package com.airlinetracker.flightdata.config;

import com.airlinetracker.flightdata.exception.RateLimitUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class RateLimitConfigTest {
    @Test
    void allowsOnlyWhenEachRedisWindowIsWithinItsLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(1L);
        var limiter = new RateLimitConfig.RateLimiter(redis, 5, 10, 20, true);

        assertThat(limiter.allowRequest()).isTrue();
        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void stopsWhenAQuotaWindowIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any())).thenReturn(2L);
        var limiter = new RateLimitConfig.RateLimiter(redis, 1, 10, 20, true);

        assertThat(limiter.allowRequest()).isFalse();
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any());
    }

    @Test
    void rejectsProviderCallsIfRedisQuotaStorageFails() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any()))
                .thenThrow(new RedisConnectionFailureException("Redis is unavailable"));
        var limiter = new RateLimitConfig.RateLimiter(redis, 5, 10, 20, true);

        assertThatThrownBy(limiter::allowRequest).isInstanceOf(RateLimitUnavailableException.class);
    }

    @Test
    void disabledLimiterDoesNotAccessRedis() {
        var limiter = new RateLimitConfig.RateLimiter(null, 0, 0, 0, false);

        assertThat(limiter.allowRequest()).isTrue();
    }

    @Test
    void reportsRedisUsageAcrossAllQuotaWindows() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("ratelimit:flightaware:minute")).thenReturn("2");
        when(values.get("ratelimit:flightaware:hour")).thenReturn("7");
        when(values.get("ratelimit:flightaware:day")).thenReturn("15");
        var limiter = new RateLimitConfig.RateLimiter(redis, 10, 200, 300, true);

        var stats = limiter.getUsageStats();

        assertThat(stats.minuteUsed).isEqualTo(2);
        assertThat(stats.minuteLimit).isEqualTo(10);
        assertThat(stats.hourUsed).isEqualTo(7);
        assertThat(stats.hourLimit).isEqualTo(200);
        assertThat(stats.dayUsed).isEqualTo(15);
        assertThat(stats.dayLimit).isEqualTo(300);
        assertThat(stats.toString()).isEqualTo("minute=2/10, hour=7/200, day=15/300");
    }

    @Test
    void reportsZeroUsageWhenRedisCountersCannotBeRead() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis is unavailable"));
        var limiter = new RateLimitConfig.RateLimiter(redis, 10, 200, 300, true);

        var stats = limiter.getUsageStats();

        assertThat(stats.minuteUsed).isZero();
        assertThat(stats.hourUsed).isZero();
        assertThat(stats.dayUsed).isZero();
    }
}
