package com.alibaba.himarket.support.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import org.junit.jupiter.api.Test;

class GrantTypeJsonTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldDeserializeEnumName() throws Exception {
        GrantType grantType = OBJECT_MAPPER.readValue("\"TRUSTED_HEADER\"", GrantType.class);

        assertEquals(GrantType.TRUSTED_HEADER, grantType);
    }

    @Test
    void shouldDeserializeWireValue() throws Exception {
        GrantType grantType = OBJECT_MAPPER.readValue("\"trusted_header\"", GrantType.class);

        assertEquals(GrantType.TRUSTED_HEADER, grantType);
    }

    @Test
    void shouldRejectBlankValue() {
        assertThrows(
                ValueInstantiationException.class,
                () -> OBJECT_MAPPER.readValue("\"  \"", GrantType.class));
    }
}
