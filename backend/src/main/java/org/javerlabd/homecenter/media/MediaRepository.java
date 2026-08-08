package org.javerlabd.homecenter.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.javerlabd.homecenter.support.Timestamps;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MediaRepository {

    private static final String COLUMNS = """
            id, source_id, category, relative_path, file_name, title, extension,
            size_bytes, modified_at, content_type, video_kind, metadata_provider,
            provider_id, description, poster_file, release_year, rating, group_key,
            group_title, season_number, episode_number, part_number, metadata_status,
            metadata_updated_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;

    public Optional<MediaItem> findById(long id) {
        Optional<MediaItem> item = jdbc.sql("SELECT " + COLUMNS + " FROM media_item WHERE id = :id")
                .param("id", id)
                .query(MediaRepository::map)
                .optional();
        return item.map(value -> attachGenres(List.of(value)).getFirst());
    }

    public Optional<MediaItem> findBySourceAndPath(long sourceId, String relativePath) {
        Optional<MediaItem> item = jdbc.sql("""
                SELECT %s FROM media_item
                 WHERE source_id = :sourceId AND relative_path = :relativePath
                """.formatted(COLUMNS))
                .param("sourceId", sourceId)
                .param("relativePath", relativePath)
                .query(MediaRepository::map)
                .optional();
        return item.map(value -> attachGenres(List.of(value)).getFirst());
    }

    public List<MediaItem> find(MediaQuery query) {
        Filter filter = Filter.from(query);
        Map<String, Object> params = new HashMap<>(filter.params());
        params.put("limit", query.limit());
        params.put("offset", query.offset());
        // Radí sa bez ohľadu na veľkosť písmen; H2 nepodporuje COLLATE v ORDER BY.
        List<MediaItem> items = jdbc.sql("SELECT " + COLUMNS + " FROM media_item" + filter.where()
                        + " ORDER BY LOWER(COALESCE(group_title, title)),"
                        + " COALESCE(season_number, 0), COALESCE(episode_number, 0),"
                        + " COALESCE(part_number, 0), COALESCE(release_year, 0), LOWER(title), id"
                        + " LIMIT :limit OFFSET :offset")
                .params(params)
                .query(MediaRepository::map)
                .list();
        return attachGenres(items);
    }

    /** Žánre, ktoré má aspoň jedna aktuálna položka knižnice. */
    public List<MediaGenre> findGenres() {
        return jdbc.sql("""
                SELECT DISTINCT genre.id, genre.name
                  FROM media_genre genre
                  JOIN media_item_genre item_genre ON item_genre.genre_id = genre.id
                 ORDER BY LOWER(genre.name), genre.id
                """)
                .query((rs, rowNum) -> new MediaGenre(rs.getLong("id"), rs.getString("name")))
                .list();
    }

    /** Názvy plagátov, na ktoré ešte odkazuje aktuálny index. */
    public Set<String> findPosterFiles() {
        return Set.copyOf(jdbc.sql("""
                SELECT DISTINCT poster_file FROM media_item
                 WHERE poster_file IS NOT NULL AND poster_file <> ''
                """)
                .query(String.class)
                .list());
    }

    public long count(MediaQuery query) {
        Filter filter = Filter.from(query);
        Long total = jdbc.sql("SELECT COUNT(*) FROM media_item" + filter.where())
                .params(filter.params())
                .query(Long.class)
                .single();
        return total == null ? 0L : total;
    }

    public Map<MediaCategory, Long> countByCategory() {
        Map<MediaCategory, Long> counts = new EnumMap<>(MediaCategory.class);
        for (MediaCategory category : MediaCategory.values()) {
            counts.put(category, 0L);
        }
        jdbc.sql("SELECT category, COUNT(*) AS total FROM media_item GROUP BY category")
                .query((rs, rowNum) -> new CategoryCount(rs.getString("category"), rs.getLong("total")))
                .list()
                .forEach(row -> parseCategory(row.category())
                        .ifPresent(category -> counts.put(category, row.total())));
        return counts;
    }

    public long totalSizeBytes() {
        Long total = jdbc.sql("SELECT COALESCE(SUM(size_bytes), 0) FROM media_item")
                .query(Long.class)
                .single();
        return total == null ? 0L : total;
    }

    /** Koľko položiek a koľko bajtov drží každý zdroj — riadky prehľadu zdrojov. */
    public Map<Long, SourceUsage> usageBySource() {
        return jdbc.sql("""
                SELECT source_id, COUNT(*) AS items, COALESCE(SUM(size_bytes), 0) AS bytes
                  FROM media_item
                 GROUP BY source_id
                """)
                .query((rs, rowNum) -> new SourceUsage(
                        rs.getLong("source_id"), rs.getLong("items"), rs.getLong("bytes")))
                .list()
                .stream()
                .collect(Collectors.toMap(SourceUsage::sourceId, usage -> usage));
    }

    /**
     * Zapíše položku a označí ju aktuálnym skenom.
     *
     * @return true, ak v indexe ešte nebola
     */
    public boolean upsert(MediaItem item, long scanId) {
        Instant now = Instant.now();
        int updated = jdbc.sql("""
                UPDATE media_item
                   SET category = :category, file_name = :fileName,
                       title = CASE WHEN metadata_provider IS NOT NULL THEN title ELSE :title END,
                       extension = :extension, size_bytes = :sizeBytes, modified_at = :modifiedAt,
                       content_type = :contentType, last_seen_scan_id = :scanId, updated_at = :updatedAt
                 WHERE source_id = :sourceId AND relative_path = :relativePath
                """)
                .params(writeParams(item, scanId, now))
                .update();
        if (updated > 0) {
            return false;
        }
        Map<String, Object> params = writeParams(item, scanId, now);
        params.put("createdAt", Timestamps.toDatabase(now));
        jdbc.sql("""
                INSERT INTO media_item (source_id, category, relative_path, file_name, title, extension,
                                        size_bytes, modified_at, content_type, last_seen_scan_id,
                                        metadata_status, created_at, updated_at)
                VALUES (:sourceId, :category, :relativePath, :fileName, :title, :extension,
                        :sizeBytes, :modifiedAt, :contentType, :scanId,
                        :metadataStatus, :createdAt, :updatedAt)
                """)
                .params(params)
                .update();
        return true;
    }

    /** Uloží rozpoznané údaje a nahradí väzby na žánre v jednej transakcii. */
    @org.springframework.transaction.annotation.Transactional
    public void saveMetadata(long mediaId, MediaMetadataUpdate metadata) {
        Instant now = Instant.now();
        jdbc.sql("""
                UPDATE media_item
                   SET title = :title, video_kind = :videoKind,
                       metadata_provider = :provider, provider_id = :providerId,
                       description = :description, poster_file = :posterFile,
                       release_year = :releaseYear, rating = :rating,
                       group_key = :groupKey, group_title = :groupTitle,
                       season_number = :seasonNumber, episode_number = :episodeNumber,
                       part_number = :partNumber, metadata_status = 'MATCHED',
                       metadata_updated_at = :metadataUpdatedAt, updated_at = :updatedAt
                 WHERE id = :id
                """)
                .param("id", mediaId)
                .param("title", metadata.title())
                .param("videoKind", metadata.kind().name())
                .param("provider", metadata.provider())
                .param("providerId", metadata.providerId())
                .param("description", metadata.description())
                .param("posterFile", metadata.posterFile())
                .param("releaseYear", metadata.releaseYear())
                .param("rating", metadata.rating())
                .param("groupKey", metadata.groupKey())
                .param("groupTitle", metadata.groupTitle())
                .param("seasonNumber", metadata.seasonNumber())
                .param("episodeNumber", metadata.episodeNumber())
                .param("partNumber", metadata.partNumber())
                .param("metadataUpdatedAt", Timestamps.toDatabase(now))
                .param("updatedAt", Timestamps.toDatabase(now))
                .update();

        jdbc.sql("DELETE FROM media_item_genre WHERE media_item_id = :mediaId")
                .param("mediaId", mediaId)
                .update();
        for (ProviderGenre genre : metadata.genres()) {
            jdbc.sql("""
                    MERGE INTO media_genre (provider, provider_id, name)
                    KEY (provider, provider_id)
                    VALUES (:provider, :providerId, :name)
                    """)
                    .param("provider", metadata.provider())
                    .param("providerId", genre.providerId())
                    .param("name", genre.name())
                    .update();
            Long genreId = jdbc.sql("""
                    SELECT id FROM media_genre
                     WHERE provider = :provider AND provider_id = :providerId
                    """)
                    .param("provider", metadata.provider())
                    .param("providerId", genre.providerId())
                    .query(Long.class)
                    .single();
            jdbc.sql("INSERT INTO media_item_genre (media_item_id, genre_id) VALUES (:mediaId, :genreId)")
                    .param("mediaId", mediaId)
                    .param("genreId", genreId)
                    .update();
        }
    }

    public void markMetadata(long mediaId, MetadataStatus status) {
        Instant now = Instant.now();
        jdbc.sql("""
                UPDATE media_item
                   SET metadata_status = :status, metadata_updated_at = :updatedAt,
                       updated_at = :updatedAt
                 WHERE id = :id
                """)
                .param("id", mediaId)
                .param("status", status.name())
                .param("updatedAt", Timestamps.toDatabase(now))
                .update();
    }

    /** Uloží zoskupenie z názvu aj vtedy, keď TMDb nie je nakonfigurované. */
    public void saveStructure(long mediaId, MediaStructure structure) {
        jdbc.sql("""
                UPDATE media_item
                   SET video_kind = :videoKind, group_key = :groupKey,
                       group_title = :groupTitle, season_number = :seasonNumber,
                       episode_number = :episodeNumber, part_number = :partNumber
                 WHERE id = :id
                """)
                .param("id", mediaId)
                .param("videoKind", structure.kind().name())
                .param("groupKey", structure.groupKey())
                .param("groupTitle", structure.groupTitle())
                .param("seasonNumber", structure.seasonNumber())
                .param("episodeNumber", structure.episodeNumber())
                .param("partNumber", structure.partNumber())
                .update();
    }

    /** Zmaže položky, ktoré posledný sken na Sambe už nenašiel. */
    public int deleteMissedBy(long sourceId, long scanId) {
        return jdbc.sql("""
                DELETE FROM media_item
                 WHERE source_id = :sourceId
                   AND (last_seen_scan_id IS NULL OR last_seen_scan_id <> :scanId)
                """)
                .param("sourceId", sourceId)
                .param("scanId", scanId)
                .update();
    }

    public int deleteBySourceId(long sourceId) {
        return jdbc.sql("DELETE FROM media_item WHERE source_id = :sourceId")
                .param("sourceId", sourceId)
                .update();
    }

    private static Map<String, Object> writeParams(MediaItem item, long scanId, Instant now) {
        Map<String, Object> params = new HashMap<>();
        params.put("sourceId", item.sourceId());
        params.put("category", item.category().name());
        params.put("relativePath", item.relativePath());
        params.put("fileName", item.fileName());
        params.put("title", item.title());
        params.put("extension", item.extension());
        params.put("sizeBytes", item.sizeBytes());
        params.put("modifiedAt", Timestamps.toDatabase(item.modifiedAt()));
        params.put("contentType", item.contentType());
        params.put("metadataStatus", item.category() == MediaCategory.VIDEO
                ? MetadataStatus.PENDING.name() : MetadataStatus.SKIPPED.name());
        params.put("scanId", scanId);
        params.put("updatedAt", Timestamps.toDatabase(now));
        return params;
    }

    private static Optional<MediaCategory> parseCategory(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MediaCategory.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static MediaItem map(ResultSet rs, int rowNum) throws SQLException {
        return new MediaItem(
                rs.getLong("id"),
                rs.getLong("source_id"),
                MediaCategory.valueOf(rs.getString("category")),
                rs.getString("relative_path"),
                rs.getString("file_name"),
                rs.getString("title"),
                rs.getString("extension"),
                rs.getLong("size_bytes"),
                Timestamps.read(rs, "modified_at"),
                rs.getString("content_type"),
                mapMetadata(rs),
                Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "updated_at"));
    }

    private static MediaMetadata mapMetadata(ResultSet rs) throws SQLException {
        MetadataStatus status = MetadataStatus.valueOf(rs.getString("metadata_status"));
        String kind = rs.getString("video_kind");
        Number providerId = (Number) rs.getObject("provider_id");
        Number releaseYear = (Number) rs.getObject("release_year");
        Number rating = (Number) rs.getObject("rating");
        Number season = (Number) rs.getObject("season_number");
        Number episode = (Number) rs.getObject("episode_number");
        Number part = (Number) rs.getObject("part_number");
        return new MediaMetadata(
                status,
                kind == null ? null : VideoKind.valueOf(kind),
                rs.getString("metadata_provider"),
                providerId == null ? null : providerId.longValue(),
                rs.getString("description"),
                rs.getString("poster_file"),
                releaseYear == null ? null : releaseYear.intValue(),
                rating == null ? null : rating.doubleValue(),
                rs.getString("group_key"),
                rs.getString("group_title"),
                season == null ? null : season.intValue(),
                episode == null ? null : episode.intValue(),
                part == null ? null : part.intValue(),
                List.of(),
                Timestamps.read(rs, "metadata_updated_at"));
    }

    private List<MediaItem> attachGenres(List<MediaItem> items) {
        List<Long> ids = items.stream()
                .filter(item -> item.metadata() != null)
                .map(MediaItem::requireId)
                .toList();
        if (ids.isEmpty()) {
            return items;
        }
        Map<Long, List<MediaGenre>> genres = jdbc.sql("""
                SELECT item_genre.media_item_id, genre.id, genre.name
                  FROM media_item_genre item_genre
                  JOIN media_genre genre ON genre.id = item_genre.genre_id
                 WHERE item_genre.media_item_id IN (:ids)
                 ORDER BY LOWER(genre.name), genre.id
                """)
                .param("ids", ids)
                .query((rs, rowNum) -> new ItemGenre(
                        rs.getLong("media_item_id"),
                        new MediaGenre(rs.getLong("id"), rs.getString("name"))))
                .list()
                .stream()
                .collect(Collectors.groupingBy(ItemGenre::mediaId,
                        Collectors.mapping(ItemGenre::genre, Collectors.toList())));
        return items.stream()
                .map(item -> item.metadata() == null ? item
                        : item.withMetadata(item.metadata().withGenres(
                                genres.getOrDefault(item.requireId(), List.of()))))
                .toList();
    }

    private record CategoryCount(String category, long total) {
    }

    /** Podmienka a jej parametre pre výpis aj pre počítanie — obe musia filtrovať rovnako. */
    private record Filter(String where, Map<String, Object> params) {

        static Filter from(MediaQuery query) {
            StringBuilder where = new StringBuilder();
            Map<String, Object> params = new HashMap<>();
            if (query.category() != null) {
                append(where, "category = :category");
                params.put("category", query.category().name());
            }
            if (query.sourceId() != null) {
                append(where, "source_id = :sourceId");
                params.put("sourceId", query.sourceId());
            }
            if (query.genreId() != null) {
                append(where, "EXISTS (SELECT 1 FROM media_item_genre filtered_genre"
                        + " WHERE filtered_genre.media_item_id = media_item.id"
                        + " AND filtered_genre.genre_id = :genreId)");
                params.put("genreId", query.genreId());
            }
            if (query.search() != null) {
                append(where, "(lower(title) LIKE :search OR lower(relative_path) LIKE :search"
                        + " OR lower(COALESCE(group_title, '')) LIKE :search"
                        + " OR EXISTS (SELECT 1 FROM media_item_genre search_item_genre"
                        + " JOIN media_genre search_genre ON search_genre.id = search_item_genre.genre_id"
                        + " WHERE search_item_genre.media_item_id = media_item.id"
                        + " AND lower(search_genre.name) LIKE :search))");
                params.put("search", "%" + query.search().toLowerCase(Locale.ROOT) + "%");
            }
            return new Filter(where.toString(), params);
        }

        private static void append(StringBuilder where, String condition) {
            where.append(where.isEmpty() ? " WHERE " : " AND ").append(condition);
        }
    }

    private record ItemGenre(long mediaId, MediaGenre genre) {
    }
}
