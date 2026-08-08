package org.javerland.homecenter.user;

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
public class UserRepository {

    private static final String COLUMNS = """
            id, username, display_name, password_hash, pin_hash, role, enabled,
            must_change_password, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public List<AppUser> findAll() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user ORDER BY role, username")
                .query(UserRepository::map)
                .list();
    }

    public Optional<AppUser> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE id = :id")
                .param("id", id)
                .query(UserRepository::map)
                .optional();
    }

    /** The username is always lowercase in the database, normalized by {@link UserService}. */
    public Optional<AppUser> findByUsername(String username) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_user WHERE username = :username")
                .param("username", username)
                .query(UserRepository::map)
                .optional();
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    public long count() {
        return jdbc.sql("SELECT COUNT(*) FROM app_user").query(Long.class).single();
    }

    /** Number of enabled administrators remaining when one specific account is excluded. */
    public long countOtherEnabledAdmins(long excludedId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM app_user
                 WHERE role = 'ADMIN' AND enabled = TRUE AND id <> :id
                """)
                .param("id", excludedId)
                .query(Long.class)
                .single();
    }

    @Transactional
    public AppUser save(AppUser user) {
        Instant now = Instant.now();
        return user.id() == null ? insert(user, now) : update(user, now);
    }

    private AppUser insert(AppUser user, Instant now) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO app_user (username, display_name, password_hash, pin_hash, role,
                                      enabled, must_change_password, created_at, updated_at)
                VALUES (:username, :displayName, :passwordHash, :pinHash, :role,
                        :enabled, :mustChangePassword, :createdAt, :updatedAt)
                """)
                .param("username", user.username())
                .param("displayName", user.displayName())
                .param("passwordHash", user.passwordHash())
                .param("pinHash", user.pinHash())
                .param("role", user.role().name())
                .param("enabled", user.enabled())
                .param("mustChangePassword", user.mustChangePassword())
                .param("createdAt", Timestamps.toDatabase(now))
                .param("updatedAt", Timestamps.toDatabase(now))
                .update(keys);

        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("Databáza nevrátila id nového používateľa");
        }
        return findById(key.longValue()).orElseThrow();
    }

    private AppUser update(AppUser user, Instant now) {
        int rows = jdbc.sql("""
                UPDATE app_user
                   SET username = :username, display_name = :displayName,
                       password_hash = :passwordHash, pin_hash = :pinHash, role = :role,
                       enabled = :enabled, must_change_password = :mustChangePassword,
                       updated_at = :updatedAt
                 WHERE id = :id
                """)
                .param("username", user.username())
                .param("displayName", user.displayName())
                .param("passwordHash", user.passwordHash())
                .param("pinHash", user.pinHash())
                .param("role", user.role().name())
                .param("enabled", user.enabled())
                .param("mustChangePassword", user.mustChangePassword())
                .param("updatedAt", Timestamps.toDatabase(now))
                .param("id", user.requireId())
                .update();
        if (rows == 0) {
            throw new UserNotFoundException(user.requireId());
        }
        return findById(user.requireId()).orElseThrow();
    }

    public void deleteById(long id) {
        jdbc.sql("DELETE FROM app_user WHERE id = :id").param("id", id).update();
    }

    private static AppUser map(ResultSet rs, int rowNum) throws SQLException {
        return new AppUser(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("display_name"),
                rs.getString("password_hash"),
                rs.getString("pin_hash"),
                Role.valueOf(rs.getString("role")),
                rs.getBoolean("enabled"),
                rs.getBoolean("must_change_password"),
                Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "updated_at"));
    }
}
