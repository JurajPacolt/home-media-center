package org.javerland.homecenter.source;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared Samba source layer for the management UI and REST API. Controllers must not
 * duplicate its logic.
 *
 * <p>Multiple sources may exist simultaneously. Every index item records its source
 * ({@code media_item.source_id}), so streaming and scanning always operate on a specific
 * source rather than assuming "the one" source.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmbSourceService {

    private final SmbSourceRepository repository;
    private final SmbGateway gateway;

    public List<SmbSource> findAll() {
        return repository.findAll();
    }

    /** Sources to scan. An empty list means there is nothing to index. */
    public List<SmbSource> findAllEnabled() {
        return repository.findAllEnabled();
    }

    public Optional<SmbSource> findById(long id) {
        return repository.findById(id);
    }

    /** Source to which a specific index item belongs. */
    public SmbSource require(long id) {
        return repository.findById(id).orElseThrow(() -> new NoActiveSourceException(
                "Zdroj s id " + id + " už neexistuje"));
    }

    /** At least one enabled source, which is required for scanning. */
    public List<SmbSource> requireEnabled() {
        List<SmbSource> enabled = repository.findAllEnabled();
        if (enabled.isEmpty()) {
            throw new NoActiveSourceException();
        }
        return enabled;
    }

    public boolean isEmpty() {
        return repository.count() == 0;
    }

    /** Source names displayed with library items, retrieved with one query instead of N. */
    public Map<Long, String> namesById() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(SmbSource::requireId, SmbSource::name));
    }

    public Map<Long, SmbSource> byId() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(SmbSource::requireId, Function.identity()));
    }

    /**
     * If an empty password is submitted, the existing value remains. The management UI
     * never displays the password, so an empty field means "do not change," not "delete."
     */
    @Transactional
    public SmbSource save(SmbSource source) {
        SmbSource normalized = normalize(withStoredPasswordIfBlank(source));
        repository.findByName(normalized.name())
                .filter(other -> source.id() == null || other.requireId() != source.id())
                .ifPresent(other -> {
                    throw new DuplicateSourceNameException(normalized.name());
                });

        SmbSource saved = repository.save(normalized);
        gateway.invalidate(saved.requireId());
        log.info("Uložený Samba zdroj {}", saved);
        return saved;
    }

    /** Test connection from the management UI, available before the source is first stored. */
    public void verify(SmbSource source) {
        gateway.verify(normalize(withStoredPasswordIfBlank(source)));
    }

    /**
     * Deleting a source also removes all its index items through the schema's
     * {@code ON DELETE CASCADE}.
     */
    @Transactional
    public void delete(long id) {
        SmbSource source = require(id);
        repository.deleteById(id);
        gateway.invalidate(id);
        log.info("Zmazaný Samba zdroj {} aj s jeho položkami indexu", source.name());
    }

    private SmbSource withStoredPasswordIfBlank(SmbSource source) {
        if (source.id() == null || (source.password() != null && !source.password().isBlank())) {
            return source;
        }
        return source.withPassword(
                repository.findById(source.id()).map(SmbSource::password).orElse(null));
    }

    private static SmbSource normalize(SmbSource source) {
        return new SmbSource(
                source.id(),
                source.name() == null || source.name().isBlank() ? source.host() : source.name().trim(),
                source.host().trim(),
                source.port() <= 0 ? SmbSource.DEFAULT_PORT : source.port(),
                source.shareName().trim(),
                SmbPaths.normalize(source.rootPath()),
                blankToNull(source.domain()),
                blankToNull(source.username()),
                blankToNull(source.password()),
                source.enabled(),
                source.createdAt(),
                source.updatedAt());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
