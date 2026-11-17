package com.airlinetracker.flightdata.client;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

class DemoFlightFixturesTest {
    @Test
    void usesStableDailyIdsThatMatchTheDisplayedDepartureDate() {
        var firstRead = DemoFlightFixtures.find("UAL123");
        var secondRead = DemoFlightFixtures.find("UAL123");

        assertThat(firstRead).hasSize(2);
        assertThat(firstRead).extracting(flight -> flight.getFaFlightId())
                .containsExactlyElementsOf(secondRead.stream().map(flight -> flight.getFaFlightId()).toList());
        firstRead.forEach(flight -> {
            String localDepartureDate = flight.getScheduledOut()
                    .atZone(ZoneId.of(flight.getOriginTimezone()))
                    .toLocalDate()
                    .format(DateTimeFormatter.BASIC_ISO_DATE);
            assertThat(flight.getFaFlightId()).contains("-DEMO-" + localDepartureDate + "-");
            assertThat(flight.getDataSource()).isEqualTo("Demo fixture");
        });
    }
}
