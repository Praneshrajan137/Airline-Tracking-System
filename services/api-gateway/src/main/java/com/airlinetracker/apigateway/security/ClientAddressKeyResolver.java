package com.airlinetracker.apigateway.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

/** Uses the address overwritten by the bundled Nginx proxy, or the socket peer for direct local calls. */
@Configuration
public class ClientAddressKeyResolver {
    @Bean
    public KeyResolver addressKeyResolver() {
        return exchange -> {
            var address = exchange.getRequest().getRemoteAddress();
            String proxyAddress = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
            String key = proxyAddress != null && !proxyAddress.isBlank()
                    ? proxyAddress
                    : address == null || address.getAddress() == null
                        ? "unknown-client"
                        : address.getAddress().getHostAddress();
            return Mono.just(key);
        };
    }
}
