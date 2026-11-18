package com.airlinetracker.llmsummary.config;

import com.airlinetracker.llmsummary.exception.OpenAIRateLimitUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class OpenAIRateLimitConfigTest {
    @Test
    void allowsOnlyWhenEveryRedisWindowIsBelowItsLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(1L);
        var limiter = new OpenAIRateLimitConfig.RateLimiter(redis, 3, 10, 100, true);

        assertThat(limiter.allowRequest()).isTrue();
        verify(redis, times(3)).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void rejectsWhenAnyWindowIsExceeded() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(4L);
        var limiter = new OpenAIRateLimitConfig.RateLimiter(redis, 3, 10, 100, true);

        assertThat(limiter.allowRequest()).isFalse();
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void rejectsProviderCallsWhenRedisIsUnavailable() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis is unavailable"));
        var limiter = new OpenAIRateLimitConfig.RateLimiter(redis, 3, 10, 100, true);

        assertThatThrownBy(limiter::allowRequest).isInstanceOf(OpenAIRateLimitUnavailableException.class);
    }

    @Test
    void disabledLimiterDoesNotContactRedis() {
        var limiter = new OpenAIRateLimitConfig.RateLimiter(null, 0, 0, 0, false);

        assertThat(limiter.allowRequest()).isTrue();
    }

    @Test
    void rejectsProviderCallWhenRedisReturnsNoQuotaCount() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), anyString())).thenReturn(null);
        var limiter = new OpenAIRateLimitConfig.RateLimiter(redis, 3, 10, 100, true);

        assertThatThrownBy(limiter::allowRequest)
                .isInstanceOf(OpenAIRateLimitUnavailableException.class)
                .hasMessage("OpenAI quota counter returned no result");
    }
}
