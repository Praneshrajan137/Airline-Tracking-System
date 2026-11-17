package com.airlinetracker.apigateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

/**
 * The traveler API is guest-accessible; account routes and unlisted endpoints stay closed.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/prometheus").permitAll()
                        .pathMatchers("/api/v1/auth/**").denyAll()
                        .pathMatchers(HttpMethod.GET,
                                "/api/v1/flight/*",
                                "/api/v1/flight/*/candidates",
                                "/api/v1/flight/*/instance/*",
                                "/api/v1/flight/*/summary",
                                "/api/v1/flight/*/summary/*").permitAll()
                        .pathMatchers(HttpMethod.POST,
                                "/api/v1/flight/*/instance/*/summary/retry").permitAll()
                        .anyExchange().denyAll()
                )
                .build();
    }

    /** Prevent Spring Boot from generating and logging a default Basic-auth password. */
    @Bean
    public ReactiveUserDetailsService noAccountUsers() {
        return username -> Mono.empty();
    }
}
