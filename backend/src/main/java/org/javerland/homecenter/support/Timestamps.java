package org.javerland.homecenter.support;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.Nullable;

/**
 * Converts between domain {@link Instant} values and {@code TIMESTAMP WITH TIME ZONE}
 * columns. It uses {@link OffsetDateTime}, the type prescribed by JDBC 4.2 for this
 * column; drivers are not required to map {@code Instant}.
 */
public final class Timestamps {

    private Timestamps() {
    }

    /** Query parameter value; all database timestamps are in UTC. */
    public static @Nullable OffsetDateTime toDatabase(@Nullable Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    public static @Nullable Instant read(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
