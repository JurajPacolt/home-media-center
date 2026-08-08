package org.javerlabd.homecenter.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/**
 * Prevod medzi {@link Instant} v doméne a stĺpcami {@code TIMESTAMP WITH TIME ZONE}.
 * Ide sa cez {@link OffsetDateTime}, lebo to je typ, ktorý pre tento stĺpec predpisuje
 * JDBC 4.2 — {@code Instant} ovládače mapovať nemusia.
 */
public final class Timestamps {

    private Timestamps() {
    }

    /** Hodnota pre parameter dotazu; v databáze sú všetky časy v UTC. */
    public static @Nullable OffsetDateTime toDatabase(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static @Nullable Instant read(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
