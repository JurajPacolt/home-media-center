package org.javerlabd.homecenter.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.support.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuthTokenRepository {

    private static final String COLUMNS = """
            id, user_id, token_hash, device_name, created_at, expires_at, last_used_at
            """;

    private final JdbcClient jdbc;

    public Optional<AuthToken> findByHash(String tokenHash) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM auth_token WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query(AuthTokenRepository::map)
                .optional();
    }

    public List<AuthToken> findByUser(long userId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM auth_token WHERE user_id = :userId"
                        + " ORDER BY last_used_at DESC NULLS LAST, created_at DESC")
                .param("userId", userId)
                .query(AuthTokenRepository::map)
                .list();
    }

    public void insert(AuthToken token) {
        jdbc.sql("""
                INSERT INTO auth_token (user_id, token_hash, device_name, created_at, expires_at)
                VALUES (:userId, :tokenHash, :deviceName, :createdAt, :expiresAt)
                """)
                .param("userId", token.userId())
                .param("tokenHash", token.tokenHash())
                .param("deviceName", token.deviceName())
                .param("createdAt", Timestamps.toDatabase(token.createdAt()))
                .param("expiresAt", Timestamps.toDatabase(token.expiresAt()))
                .update();
    }

    public void touch(long id, Instant when) {
        jdbc.sql("UPDATE auth_token SET last_used_at = :when WHERE id = :id")
                .param("when", Timestamps.toDatabase(when))
                .param("id", id)
                .update();
    }

    public void deleteByHash(String tokenHash) {
        jdbc.sql("DELETE FROM auth_token WHERE token_hash = :hash").param("hash", tokenHash).update();
    }

    public int deleteByUser(long userId) {
        return jdbc.sql("DELETE FROM auth_token WHERE user_id = :userId")
                .param("userId", userId)
                .update();
    }

    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM auth_token WHERE id = :id").param("id", id).update();
    }

    public int deleteExpired(Instant now) {
        return jdbc.sql("DELETE FROM auth_token WHERE expires_at <= :now")
                .param("now", Timestamps.toDatabase(now))
                .update();
    }

    private static AuthToken map(ResultSet rs, int rowNum) throws SQLException {
        return new AuthToken(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("token_hash"),
                rs.getString("device_name"),
                Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "expires_at"),
                Timestamps.read(rs, "last_used_at"));
    }
}
