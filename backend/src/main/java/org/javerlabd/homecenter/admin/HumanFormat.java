package org.javerlabd.homecenter.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Formátovanie pre šablóny — volá sa z Thymeleafu ako {@code ${@humanFormat.size(...)}}. */
@Component("humanFormat")
public class HumanFormat {

    private static final String[] UNITS = { "B", "kB", "MB", "GB", "TB", "PB" };

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d.M.uuuu HH:mm:ss").withZone(ZoneId.systemDefault());

    public String size(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.of("sk"), "%.1f %s", value, UNITS[unit]);
    }

    public String dateTime(@Nullable Instant instant) {
        return instant == null ? "—" : DATE_TIME.format(instant);
    }

    public String duration(long seconds) {
        Duration duration = Duration.ofSeconds(Math.max(seconds, 0));
        long hours = duration.toHours();
        if (hours > 0) {
            return "%d h %d min".formatted(hours, duration.toMinutesPart());
        }
        long minutes = duration.toMinutes();
        return minutes > 0
                ? "%d min %d s".formatted(minutes, duration.toSecondsPart())
                : "%d s".formatted(duration.toSecondsPart());
    }
}
