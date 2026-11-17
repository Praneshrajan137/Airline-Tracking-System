package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.config.RateLimitConfig;
import com.airlinetracker.flightdata.dto.FlightData;
import com.airlinetracker.flightdata.exception.FlightNotFoundException;
import com.airlinetracker.flightdata.exception.RateLimitExceededException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Client for FlightAware AeroAPI. The demo profile serves clearly labeled local fixtures. */
@Slf4j
@Component
public class FlightAwareClient {
    private static final int TIMEOUT_SECONDS = 5;
    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final WebClient webClient;
    private final String apiKey;
    private final RateLimitConfig.RateLimiter rateLimiter;
    private final boolean demoMode;

    @Autowired
    public FlightAwareClient(
            @Value("${flightaware.base-url:https://aeroapi.flightaware.com/aeroapi}") String baseUrl,
            @Value("${flightaware.api-key:}") String apiKey,
            RateLimitConfig.RateLimiter rateLimiter,
            @Value("${demo.enabled:false}") boolean demoMode) {
        this(WebClient.builder().baseUrl(baseUrl).build(), apiKey, rateLimiter, demoMode);
    }

    public FlightAwareClient(WebClient webClient, String apiKey, RateLimitConfig.RateLimiter rateLimiter) {
        this(webClient, apiKey, rateLimiter, false);
    }

    private FlightAwareClient(WebClient webClient, String apiKey,
                              RateLimitConfig.RateLimiter rateLimiter, boolean demoMode) {
        this.webClient = webClient;
        this.apiKey = apiKey;
        this.rateLimiter = rateLimiter;
        this.demoMode = demoMode;
        if (!demoMode && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalStateException("FLIGHTAWARE_API_KEY is required unless the demo profile is enabled");
        }
    }

    /** Compatibility helper for callers that expect one record. */
    public Mono<FlightData> getFlightByIdent(String ident) {
        return getFlightsByIdent(ident).flatMap(flights -> flights.isEmpty()
                ? Mono.error(new FlightNotFoundException(ident))
                : Mono.just(flights.get(0)));
    }

    /** Return every matching instance so callers can select the correct date and leg. */
    public Mono<List<FlightData>> getFlightsByIdent(String ident) {
        if (demoMode) {
            List<FlightData> fixtures = DemoFlightFixtures.find(ident);
            return fixtures.isEmpty() ? Mono.error(new FlightNotFoundException(ident)) : Mono.just(fixtures);
        }
        if (!rateLimiter.allowRequest()) {
            return Mono.error(new RateLimitExceededException(
                    "Flight data provider limit reached. Please retry later."));
        }

        return webClient.get()
                .uri("/flights/{ident}", ident)
                .header("x-apikey", apiKey)
                .exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class).map(this::parseCandidates);
                    }
                    if (response.statusCode().value() == 404) {
                        return response.releaseBody().then(Mono.error(new FlightNotFoundException(ident)));
                    }
                    if (response.statusCode().value() == 429) {
                        return response.releaseBody().then(Mono.error(new RateLimitExceededException(
                                "Flight data provider rate limit reached. Please retry later.")));
                    }
                    int status = response.statusCode().value();
                    HttpStatus resolved = HttpStatus.resolve(status);
                    return response.releaseBody().then(Mono.error(new IllegalStateException(
                            "Flight data provider returned HTTP " + (resolved == null ? status : resolved.value()))));
                })
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .flatMap(flights -> flights.isEmpty()
                        ? Mono.error(new FlightNotFoundException(ident))
                        : Mono.just(flights))
                .doOnError(error -> log.warn("Flight lookup failed for {} ({})", ident,
                        error.getClass().getSimpleName()));
    }

    private List<FlightData> parseCandidates(String responseBody) {
        try {
            JsonNode rows = JSON.readTree(responseBody).path("flights");
            if (!rows.isArray()) return List.of();
            List<FlightData> flights = new ArrayList<>();
            for (JsonNode row : rows) {
                ObjectNode normalized = row.deepCopy();
                normalizeAirport(normalized, "origin");
                normalizeAirport(normalized, "destination");
                normalizePosition(normalized);
                flights.add(JSON.treeToValue(normalized, FlightData.class));
            }
            return flights;
        } catch (IOException exception) {
            throw new IllegalStateException("Flight data provider returned an unreadable response", exception);
        }
    }

    private void normalizeAirport(ObjectNode flight, String field) {
        JsonNode airport = flight.get(field);
        if (airport == null || !airport.isObject()) return;
        String code = firstText(airport, "code", "code_icao", "code_iata");
        if (code != null) flight.put(field, code);
        copyText(airport, "name", flight, field + "_name");
        copyText(airport, "city", flight, field + "_city");
        copyText(airport, "timezone", flight, field + "_timezone");
    }

    private void normalizePosition(ObjectNode flight) {
        JsonNode position = flight.get("last_position");
        if (position == null || !position.isObject()) return;
        copyIfAbsent(position, "latitude", flight);
        copyIfAbsent(position, "longitude", flight);
        copyIfAbsent(position, "altitude", flight);
        copyIfAbsent(position, "groundspeed", flight);
        copyText(position, "timestamp", flight, "last_position_at");
    }

    private static void copyText(JsonNode from, String source, ObjectNode to, String target) {
        JsonNode value = from.get(source);
        if (value != null && !value.isNull()) to.put(target, value.asText());
    }

    private static void copyIfAbsent(JsonNode from, String field, ObjectNode to) {
        if (to.has(field) && !to.get(field).isNull()) return;
        JsonNode value = from.get(field);
        if (value != null && !value.isNull()) to.set(field, value);
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }
}
