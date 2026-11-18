package com.airlinetracker.flightdata.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AirportCodeDeserializerTest {
    private final AirportCodeDeserializer deserializer = new AirportCodeDeserializer();
    private final DeserializationContext context = mock(DeserializationContext.class);

    @Test
    void returnsNullForJsonNull() throws Exception {
        assertThat(deserialize(NullNode.getInstance())).isNull();
    }

    @Test
    void acceptsLegacyStringValues() throws Exception {
        assertThat(deserializeNode("\"KORD\"")).isEqualTo("KORD");
    }

    @Test
    void extractsPrimaryCodeFromAirportObject() throws Exception {
        assertThat(deserializeNode("{\"code\":\"YAYE\",\"code_icao\":\"YAYE\"}"))
                .isEqualTo("YAYE");
    }

    @Test
    void fallsBackToIcaoCodeWhenPrimaryCodeIsNull() throws Exception {
        assertThat(deserializeNode("{\"code\":null,\"code_icao\":\"YAYE\"}"))
                .isEqualTo("YAYE");
    }

    @Test
    void returnsNullForAirportObjectWithoutSupportedCodes() throws Exception {
        assertThat(deserializeNode("{\"name\":\"Unknown\"}")).isNull();
    }

    @Test
    void returnsNullForUnexpectedJsonValues() throws Exception {
        assertThat(deserializeNode("[]")).isNull();
    }

    private String deserializeNode(String json) throws Exception {
        JsonParser parser = new com.fasterxml.jackson.databind.ObjectMapper().createParser(json);
        parser.setCodec(new com.fasterxml.jackson.databind.ObjectMapper());
        return deserializer.deserialize(parser, context);
    }

    private String deserialize(JsonNode node) throws Exception {
        JsonParser parser = mock(JsonParser.class);
        ObjectCodec codec = mock(ObjectCodec.class);
        when(parser.getCodec()).thenReturn(codec);
        when(codec.readTree(parser)).thenReturn(node);
        return deserializer.deserialize(parser, context);
    }
}
