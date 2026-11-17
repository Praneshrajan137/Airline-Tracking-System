package com.airlinetracker.flightdata.client;

import com.airlinetracker.flightdata.dto.FlightData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Stable, clearly labeled sample records for offline academic demonstrations. */
final class DemoFlightFixtures {
    private DemoFlightFixtures() {}

    static List<FlightData> find(String ident) {
        Instant now = Instant.now();
        return switch (ident.toUpperCase(Locale.ROOT)) {
            case "UAL123" -> List.of(
                    flight(fixtureId("UAL123", now.minusSeconds(7_200), "America/Chicago", 1), "UAL123", "En-Route / In Flight",
                            "KORD", "Chicago O'Hare", "Chicago", "America/Chicago",
                            "KLAX", "Los Angeles International", "Los Angeles", "America/Los_Angeles",
                            now.minusSeconds(7_200), now.minusSeconds(6_900), now.plusSeconds(3_600), null,
                            "B738", 39.8283, -98.5795, 35_000, 452, now.minusSeconds(240)),
                    flight(fixtureId("UAL123", now.plusSeconds(86_400), "America/Chicago", 2), "UAL123", "Scheduled",
                            "KORD", "Chicago O'Hare", "Chicago", "America/Chicago",
                            "KLAX", "Los Angeles International", "Los Angeles", "America/Los_Angeles",
                            now.plusSeconds(86_400), null, now.plusSeconds(95_400), null,
                            "B738", null, null, null, null, null));
            case "BA178" -> List.of(flight(fixtureId("BA178", now.plusSeconds(7_200), "America/New_York", 1), "BA178", "Scheduled",
                    "KJFK", "John F. Kennedy International", "New York", "America/New_York",
                    "EGLL", "Heathrow", "London", "Europe/London",
                    now.plusSeconds(7_200), null, now.plusSeconds(28_800), null,
                    "B772", null, null, null, null, null));
            case "DL400" -> List.of(flight(fixtureId("DL400", now.minusSeconds(14_400), "America/New_York", 1), "DL400", "Landed",
                    "KATL", "Hartsfield-Jackson Atlanta International", "Atlanta", "America/New_York",
                    "KJFK", "John F. Kennedy International", "New York", "America/New_York",
                    now.minusSeconds(14_400), now.minusSeconds(14_100), now.minusSeconds(10_800), now.minusSeconds(10_920),
                    "A321", null, null, null, null, null));
            default -> List.of();
        };
    }

    private static String fixtureId(String ident, Instant departure, String timezone, int sequence) {
        LocalDate date = departure.atZone(ZoneId.of(timezone)).toLocalDate();
        return ident + "-DEMO-" + date.format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + sequence;
    }

    private static FlightData flight(String id, String ident, String status,
                                     String origin, String originName, String originCity, String originZone,
                                     String destination, String destinationName, String destinationCity, String destinationZone,
                                     Instant scheduledOut, Instant actualOut, Instant scheduledIn, Instant actualIn,
                                     String aircraft, Double latitude, Double longitude, Integer altitude,
                                     Integer groundspeed, Instant positionAt) {
        return FlightData.builder()
                .faFlightId(id).ident(ident).status(status)
                .origin(origin).originName(originName).originCity(originCity).originTimezone(originZone)
                .destination(destination).destinationName(destinationName).destinationCity(destinationCity)
                .destinationTimezone(destinationZone)
                .scheduledOut(scheduledOut).actualOut(actualOut).scheduledIn(scheduledIn).actualIn(actualIn)
                .aircraftType(aircraft).latitude(latitude).longitude(longitude).altitude(altitude)
                .groundspeed(groundspeed).lastPositionAt(positionAt).dataSource("Demo fixture")
                .build();
    }
}
