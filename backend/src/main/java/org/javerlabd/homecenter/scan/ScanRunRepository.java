package org.javerlabd.homecenter.scan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.support.Timestamps;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ScanRunRepository {

    private static final String COLUMNS = """
            id, source_id, trigger_type, status, started_at, finished_at, directories_scanned,
            files_seen, items_added, items_updated, items_removed, error_message
            """;

    private final JdbcClient jdbc;

    @Transactional
    public ScanRun start(long sourceId, ScanTrigger trigger) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO scan_run (source_id, trigger_type, status, started_at)
                VALUES (:sourceId, :trigger, :status, :startedAt)
                """)
                .param("sourceId", sourceId)
                .param("trigger", trigger.name())
                .param("status", ScanStatus.RUNNING.name())
                .param("startedAt", Timestamps.toDatabase(Instant.now()))
                .update(keys);

        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Databáza nevrátila id skenu");
        }
        return findById(key.longValue()).orElseThrow();
    }

    public void updateProgress(long scanId, ScanCounters counters) {
        jdbc.sql("""
                UPDATE scan_run
                   SET directories_scanned = :directories, files_seen = :files,
                       items_added = :added, items_updated = :updated
                 WHERE id = :id
                """)
                .param("directories", counters.getDirectoriesScanned())
                .param("files", counters.getFilesSeen())
                .param("added", counters.getItemsAdded())
                .param("updated", counters.getItemsUpdated())
                .param("id", scanId)
                .update();
    }

    public void finish(long scanId, ScanStatus status, ScanCounters counters, @Nullable String errorMessage) {
        jdbc.sql("""
                UPDATE scan_run
                   SET status = :status, finished_at = :finishedAt, directories_scanned = :directories,
                       files_seen = :files, items_added = :added, items_updated = :updated,
                       items_removed = :removed, error_message = :error
                 WHERE id = :id
                """)
                .param("status", status.name())
                .param("finishedAt", Timestamps.toDatabase(Instant.now()))
                .param("directories", counters.getDirectoriesScanned())
                .param("files", counters.getFilesSeen())
                .param("added", counters.getItemsAdded())
                .param("updated", counters.getItemsUpdated())
                .param("removed", counters.getItemsRemoved())
                .param("error", errorMessage)
                .param("id", scanId)
                .update();
    }

    /**
     * If the server failed during a scan, its row would remain RUNNING forever. Such
     * runs are closed at startup.
     */
    public int markInterrupted() {
        return jdbc.sql("""
                UPDATE scan_run
                   SET status = :failed, finished_at = :finishedAt, error_message = :error
                 WHERE status = :running
                """)
                .param("failed", ScanStatus.FAILED.name())
                .param("finishedAt", Timestamps.toDatabase(Instant.now()))
                .param("error", "Sken prerušil reštart servera")
                .param("running", ScanStatus.RUNNING.name())
                .update();
    }

    public Optional<ScanRun> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM scan_run WHERE id = :id")
                .param("id", id)
                .query(ScanRunRepository::map)
                .optional();
    }

    public Optional<ScanRun> findLatest() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM scan_run ORDER BY id DESC LIMIT 1")
                .query(ScanRunRepository::map)
                .optional();
    }

    public List<ScanRun> findRecent(int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM scan_run ORDER BY id DESC LIMIT :limit")
                .param("limit", Math.clamp(limit, 1, 100))
                .query(ScanRunRepository::map)
                .list();
    }

    /**
     * Latest run for each source. The highest {@code id} within a source is used; runs
     * are created sequentially, so this is also the latest chronologically.
     */
    public Map<Long, ScanRun> findLatestBySource() {
        return jdbc.sql("""
                SELECT %s FROM scan_run r
                 WHERE r.source_id IS NOT NULL
                   AND r.id = (SELECT MAX(i.id) FROM scan_run i WHERE i.source_id = r.source_id)
                """.formatted(COLUMNS))
                .query(ScanRunRepository::map)
                .list()
                .stream()
                .collect(Collectors.toMap(run -> run.sourceId(), Function.identity()));
    }

    private static ScanRun map(ResultSet rs, int rowNum) throws SQLException {
        long sourceId = rs.getLong("source_id");
        // wasNull() always applies only to the latest read, so evaluate it immediately.
        Long sourceIdOrNull = rs.wasNull() ? null : sourceId;
        return new ScanRun(
                rs.getLong("id"),
                sourceIdOrNull,
                ScanTrigger.valueOf(rs.getString("trigger_type")),
                ScanStatus.valueOf(rs.getString("status")),
                Timestamps.read(rs, "started_at"),
                Timestamps.read(rs, "finished_at"),
                rs.getInt("directories_scanned"),
                rs.getInt("files_seen"),
                rs.getInt("items_added"),
                rs.getInt("items_updated"),
                rs.getInt("items_removed"),
                rs.getString("error_message"));
    }
}
