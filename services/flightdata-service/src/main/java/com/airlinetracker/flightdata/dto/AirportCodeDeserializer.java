package com.airlinetracker.flightdata.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Custom deserializer for FlightAware airport objects.
 *
 * FlightAware AeroAPI returns origin/destination as nested objects:
 * {
 *   "code": "YAYE",
 *   "code_icao": "YAYE",
 *   "code_iata": "AYQ",
 *   "name": "Ayers Rock",
 *   "city": "Yulara",
 *   "timezone": "Australia/Darwin"
 * }
 *
 * This deserializer extracts just the ICAO code (the "code" field)
 * and returns it as a simple String for our FlightData DTO.
 */
@Slf4j
public class AirportCodeDeserializer extends JsonDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);

        // Handle null case
        if (node == null || node.isNull()) {
            return null;
        }

        // If it's a string (backward compatibility), return as-is
        if (node.isTextual()) {
            return node.asText();
        }

        // Extract ICAO code from nested object
        if (node.isObject()) {
            // Primary: use "code" field (ICAO code)
            JsonNode codeNode = node.get("code");
            if (codeNode != null && !codeNode.isNull()) {
                String code = codeNode.asText();
                log.debug("Extracted airport code: {}", code);
                return code;
            }

            // Fallback: use "code_icao" if "code" is missing
            JsonNode icaoNode = node.get("code_icao");
            if (icaoNode != null && !icaoNode.isNull()) {
                String code = icaoNode.asText();
                log.debug("Extracted airport code_icao: {}", code);
                return code;
            }

            log.warn("Airport object missing 'code' and 'code_icao' fields: {}", node);
            return null;
        }

        log.warn("Unexpected airport format: {}", node);
        return null;
    }
}
