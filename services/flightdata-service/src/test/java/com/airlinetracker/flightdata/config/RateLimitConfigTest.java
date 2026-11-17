package com.airlinetracker.flightdata.config;

import com.airlinetracker.flightdata.exception.RateLimitUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
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
}
