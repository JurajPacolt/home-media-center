package org.javerlabd.homecenter.source;

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
 * Spoločná vrstva nad Samba zdrojmi pre management UI aj REST API — controllery
 * si sem nesmú nič duplikovať.
 *
 * <p>Zdrojov môže byť viac naraz. Každá položka indexu vie, z ktorého pochádza
 * ({@code media_item.source_id}), takže streamovanie aj sken pracujú vždy s konkrétnym
 * zdrojom, nie s „tým jedným“.
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

    /** Zdroje, ktoré sa majú skenovať. Prázdny zoznam znamená, že nie je čo indexovať. */
    public List<SmbSource> findAllEnabled() {
        return repository.findAllEnabled();
    }

    public Optional<SmbSource> findById(long id) {
        return repository.findById(id);
    }

    /** Zdroj, ku ktorému patrí konkrétna položka indexu. */
    public SmbSource require(long id) {
        return repository.findById(id).orElseThrow(() -> new NoActiveSourceException(
                "Zdroj s id " + id + " už neexistuje"));
    }

    /** Aspoň jeden zapnutý zdroj — bez neho sa sken nemá o čo oprieť. */
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

    /** Názvy zdrojov pre zobrazenie pri položkách knižnice — jeden dotaz namiesto N. */
    public Map<Long, String> namesById() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(SmbSource::requireId, SmbSource::name));
    }

    public Map<Long, SmbSource> byId() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(SmbSource::requireId, Function.identity()));
    }

    /**
     * Ak sa v hesle príde prázdna hodnota, ostáva pôvodné — management UI heslo
     * nikdy nezobrazuje, takže prázdne pole znamená „nemeniť“, nie „zmazať“.
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

    /** Skúšobné pripojenie z management UI — funguje aj pred prvým uložením zdroja. */
    public void verify(SmbSource source) {
        gateway.verify(normalize(withStoredPasswordIfBlank(source)));
    }

    /**
     * Zmazanie zdroja vyhodí z indexu aj všetky jeho položky — stará sa o to
     * {@code ON DELETE CASCADE} v schéme.
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
