package org.javerland.homecenter.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.javerland.homecenter.support.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class SmbSourceRepository {

    private static final String COLUMNS = """
            id, name, host, port, share_name, root_path, domain, username, password,
            enabled, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public List<SmbSource> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM smb_source ORDER BY name")
                .query(SmbSourceRepository::map)
                .list();
    }

    public Optional<SmbSource> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM smb_source WHERE id = :id")
                .param("id", id)
                .query(SmbSourceRepository::map)
                .optional();
    }

    /** Sources to scan. Their order is stable because the scan processes them sequentially. */
    public List<SmbSource> findAllEnabled() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM smb_source WHERE enabled = TRUE ORDER BY name, id")
                .query(SmbSourceRepository::map)
                .list();
    }

    /** The name is unique regardless of case and identifies sources in the UI. */
    public Optional<SmbSource> findByName(String name) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM smb_source WHERE LOWER(name) = LOWER(:name)")
                .param("name", name)
                .query(SmbSourceRepository::map)
                .optional();
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM smb_source").query(Long.class).single();
    }

    @Transactional
    public SmbSource save(SmbSource source) {
        Instant now = Instant.now();
        return source.id() == null ? insert(source, now) : update(source, now);
    }

    private SmbSource insert(SmbSource source, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO smb_source (name, host, port, share_name, root_path, domain, username,
                                        password, enabled, created_at, updated_at)
                VALUES (:name, :host, :port, :shareName, :rootPath, :domain, :username,
                        :password, :enabled, :createdAt, :updatedAt)
                """)
                .param("name", source.name())
                .param("host", source.host())
                .param("port", source.port())
                .param("shareName", source.shareName())
                .param("rootPath", source.rootPath())
                .param("domain", source.domain())
                .param("username", source.username())
                .param("password", source.password())
                .param("enabled", source.enabled())
                .param("createdAt", Timestamps.toDatabase(now))
                .param("updatedAt", Timestamps.toDatabase(now))
                .update(keys);

        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Databáza nevrátila id nového zdroja");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private SmbSource update(SmbSource source, Instant now) {
        int rows = jdbc.sql("""
                UPDATE smb_source
                   SET name = :name, host = :host, port = :port, share_name = :shareName,
                       root_path = :rootPath, domain = :domain, username = :username,
                       password = :password, enabled = :enabled, updated_at = :updatedAt
                 WHERE id = :id
                """)
                .param("name", source.name())
                .param("host", source.host())
                .param("port", source.port())
                .param("shareName", source.shareName())
                .param("rootPath", source.rootPath())
                .param("domain", source.domain())
                .param("username", source.username())
                .param("password", source.password())
                .param("enabled", source.enabled())
                .param("updatedAt", Timestamps.toDatabase(now))
                .param("id", source.requireId())
                .update();
        if (rows == 0) {
            throw new IllegalStateException("Zdroj s id " + source.id() + " neexistuje");
        }
        return findById(source.requireId()).orElseThrow();
    }

    public void deleteById(long id) {
        jdbc.sql("DELETE FROM smb_source WHERE id = :id").param("id", id).update();
    }

    private static SmbSource map(ResultSet rs, int rowNum) throws SQLException {
        return new SmbSource(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("host"),
                rs.getInt("port"),
                rs.getString("share_name"),
                rs.getString("root_path"),
                rs.getString("domain"),
                rs.getString("username"),
                rs.getString("password"),
                rs.getBoolean("enabled"),
                Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "updated_at"));
    }
}
