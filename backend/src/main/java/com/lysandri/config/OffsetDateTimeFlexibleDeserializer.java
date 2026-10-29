package com.lysandri.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Deserializador flexible para campos OffsetDateTime.
 * Acepta formatos estándar ISO-8601 (con zona/offset) y fechas simples YYYY-MM-DD provenientes de selectores HTML.
 */
public class OffsetDateTimeFlexibleDeserializer extends JsonDeserializer<OffsetDateTime> {

    @Override
    public OffsetDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        text = text.trim();
        try {
            return OffsetDateTime.parse(text);
        } catch (Exception e1) {
            try {
                LocalDate localDate = LocalDate.parse(text);
                return localDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
            } catch (Exception e2) {
                try {
                    LocalDateTime ldt = LocalDateTime.parse(text);
                    return ldt.atOffset(ZoneOffset.UTC);
                } catch (Exception e3) {
                    throw ctxt.weirdStringException(text, OffsetDateTime.class,
                            "Formato de fecha inválido. Utilice YYYY-MM-DD o formato ISO-8601");
                }
            }
        }
    }
}

