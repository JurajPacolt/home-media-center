# Technologické rozhodnutia

## 2026-08-05 — Výber základného stacku

**Server: Java 25 + Spring Boot 4.1** — REST API + webové management UI cez Thymeleaf
**Klient: natívna Android TV aplikácia (Kotlin)**

### Prečo

- **Virtuálne vlákna (Java 21+, teda aj 25) sedia presne na jadro aplikácie.**
  Streamovanie zo Samby je blocking IO a `SmbRandomAccessFile` je blokujúce API.
  Virtuálne vlákna to zvládnu bez vyčerpania thread poolu — stačí zapnúť
  `spring.threads.virtual.enabled=true` a netreba ladiť veľkosti poolov.
- **Java 25 je LTS.** Dlhá podpora bez skákania po verziách, čo pri projekte
  na roky dopredu dáva zmysel.
- Zvažovaná alternatíva **Python + FastAPI bola zamietnutá** — knižnica
  `smbprotocol` je synchronná a bojovala by s async modelom FastAPI.
- Budúci smart asistent nevyžaduje Python v procese — reč, jazykový model aj
  ovládanie domácnosti sa volajú cez HTTP/MQTT z ľubovoľného jazyka.

### Serverová časť

| Úloha | Technológia |
|---|---|
| Jazyk | Java 25 (LTS) |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11) |
| Súbežnosť | virtuálne vlákna — `spring.threads.virtual.enabled=true` |
| Management UI | Thymeleaf (server-side rendering, natívne v Spring Boote) |
| DTO | Java `record` |
| Build | Maven, kompilácia s JDK 25 |
| SMB prístup | smbj (SMB2/3), pozičné čítanie cez `share.File` |
| Index médií | H2 + Flyway (pôvodne SQLite, zmenené 2026-08-06) |
| Metadáta súborov | ffprobe (FFmpeg) |
| Metadáta filmov | TMDb API |
| Náhľady fotiek | thumbnailator |
| Nasadenie | Docker + docker-compose |

### Management UI (Thymeleaf)

| Úloha | Technológia |
|---|---|
| CSS framework | Bootstrap 5 |
| JS | jQuery |
| Video v prehliadači | Video.js cez WebJar |
| Grafy | Chart.js — pridá sa až keď bude treba |

Knižnice sa servírujú **lokálne cez WebJars**, nie z CDN. Server beží v domácej
sieti a management UI musí fungovať aj bez pripojenia na internet.

### Klientská časť

| Úloha | Technológia |
|---|---|
| UI | Jetpack Compose for TV (`androidx.tv:tv-material`) — nie Leanback |
| Prehrávač | Media3 / ExoPlayer |
| REST | Retrofit + OkHttp + kotlinx.serialization |
| Obrázky | Coil |
| DI | Hilt |
| Offline cache | Room |

### Zásady, ktoré z toho plynú

1. **Server je jediný, kto pozná SMB credentials.** Klient nesiaha na Sambu
   priamo, server súbory proxuje cez HTTP s Range requestami.
2. **Samba sa neskenuje pri každom requeste.** Sken beží na pozadí naplánovane
   plus na manuálne vyžiadanie, REST API číta z lokálneho indexu.
3. **Zatiaľ žiadne transkódovanie.** Server súbory len preposiela (direct play).
   FFmpeg transkódovanie sa rieši až pri konkrétnom nekompatibilnom súbore.
4. **Konfigurácia SMB zdroja patrí do Thymeleaf UI na serveri, nie do TV appky.**
   Nastavovať sieťové cesty a heslá D-padom je utrpenie. Na TV ostávajú tri
   dlaždice: Videá / Fotky / Hudba.
5. **Thymeleaf UI a REST API sú oddelené vrstvy nad rovnakou service vrstvou.**
   Admin controllery vracajú HTML, API controllery JSON — žiadna z nich
   neobsahuje logiku, ktorú by tá druhá nemala k dispozícii.
6. **Server je Java, klient Kotlin — modely sa nezdieľajú.** Zmluvou medzi nimi
   je OpenAPI spec (springdoc), z ktorej sa dá klientske API vygenerovať.
   Kotlin konzumuje JSON z Java recordov bez problémov, ale kontrakt treba
   držať na jednom mieste, nie prepisovať ručne na oboch stranách.

### Otvorené

- Na akom hardvéri server pobeží. Pri Raspberry Pi je ~300 MB baseline pamäte
  JVM citeľných — riešením je skôr ladenie heapu, prípadne GraalVM native image,
  než zmena frameworku.

---

## 2026-08-06 — Založenie servera

Základný package je `org.javerlabd.homecenter`. Vznikol modul `backend/`
s funkčným skeletom: index médií, sken Samby, REST API, streamovanie
s Range requestami a Thymeleaf management UI.

### Build: Maven

**Zvolený Maven**, nie Gradle. Dôvod je praktický — na stroji je Gradle 6.4.1,
ktorý Javu 25 nepodporuje ani zďaleka, kým Maven 3.9.6 s ňou beží bez problémov.
Maven zároveň nemá démona, ktorý by pri Springu treba ladiť.

Verzia projektu: `org.javerlabd:homecenter:0.1.0-SNAPSHOT`.

### Uzavreté otvorené otázky

**Thymeleaf so Spring Framework 7 funguje.** Artefakt `thymeleaf-spring7`
neexistuje a ani nevznikne — Spring Boot 4.1 používa `thymeleaf-spring6`
(Thymeleaf 3.1.5.RELEASE) a ten je so Springom 7 kompatibilný. Ťahá si ho
`spring-boot-starter-thymeleaf` cez modul `spring-boot-thymeleaf`, netreba nič
pridávať ručne.

### Opravy pôvodného zápisu

**smbj nemá `SmbRandomAccessFile`.** Tá trieda patrí knižnici jcifs-ng.
Random access v smbj rieši `com.hierynomus.smbj.share.File`:

```java
int read(byte[] buffer, long fileOffset, int bufferOffset, int length)
```

Čítanie na absolútnu pozíciu je presne to, čo pretáčanie potrebuje — server
teda nemá dôvod siahať po jcifs-ng. Na tomto volaní stojí `SmbInputStream`,
ktorého `skip()` je O(1) posun pozície bez prenosu bajtov.

### Spring Boot 4 je rozdrobený na moduly

Oproti Boot 3 nestačia staré artefakty, auto-konfigurácie sa presťahovali:

| Čo | Kde to je v Boot 4 |
|---|---|
| Flyway auto-config | `org.springframework.boot:spring-boot-flyway` (samotný `flyway-core` nespustí migrácie) |
| `@WebMvcTest` | `org.springframework.boot:spring-boot-starter-webmvc-test`, balík `org.springframework.boot.webmvc.test.autoconfigure` |
| Thymeleaf integrácia | `spring-boot-thymeleaf` → `thymeleaf-spring6` |

Podporu H2 má `flyway-core` v sebe, samostatný modul netreba.

### Prístup k databáze: JdbcClient, nie JPA

Index sa číta a zapisuje cez **Spring `JdbcClient`** s ručnými `RowMapper`-mi.
Pre pár tabuliek indexu je ORM zbytočná vrstva a Flyway drží schému tak či tak.

### Konfigurácia SMB zdroja je v databáze

Zdroj sa nastavuje za behu z management UI, nie v `application.yml` — inak by
sa pri zmene hesla musel reštartovať server. Tabuľka `smb_source`, heslo sa
nikdy nedostane do DTO, šablóny ani do `toString()`.

**Otvorené:** heslo je v databáze v otvorenom tvare. Pre server v domácej sieti
to zatiaľ stačí, ale šifrovanie citlivých stĺpcov je vec, ktorú treba doriešiť
skôr, než sa mediacentrum otvorí mimo LAN.

---

## 2026-08-06 — H2 namiesto SQLite, pridaný Lombok

### Index beží na H2

**Zmena oproti pôvodnému zápisu:** index nie je SQLite, ale **H2** v súborovom
režime (`jdbc:h2:file:./data/homecenter`, súbor `data/homecenter.mv.db`).
Flyway ostáva.

Čo to prinieslo:

- **Natívne dátové typy.** `TIMESTAMP WITH TIME ZONE` a `BOOLEAN` namiesto
  textu a `INTEGER 0/1`. Padol tým celý obchádzkový aparát okolo formátovania
  časov — SQLite nemá dátumový typ, takže sa časy ukladali ako reťazce
  s fixnou dĺžkou, aby sa dali v SQL zoradiť.
- **Štandardné SQL.** Migrácia používa `GENERATED BY DEFAULT AS IDENTITY`, nie
  SQLite-ovské `AUTOINCREMENT`. Prípadný presun na Postgres by bol menší zásah.
- **Flyway bez extra modulu** — podpora H2 je priamo vo `flyway-core`.
- Žiadna natívna knižnica; `sqlite-jdbc` si so sebou ťahá binárku pre platformu.

Za čo sa platí: formát súboru H2 sa medzi major verziami už v minulosti
nekompatibilne menil (1.4 → 2.x). Pri upgrade H2 preto rátaj s tým, že súbor
možno nepôjde otvoriť. Pre tento projekt je to prijateľné — index je odvodené
dáta, po zmazaní `data/` ho ďalší sken postaví odznova. Neplatí to pre nič,
čo by sa do databázy pridalo neskôr a nedalo sa zrekonštruovať.

`ORDER BY ... COLLATE NOCASE` zo SQLite H2 nepozná, výpis knižnice sa radí
cez `LOWER(title)`.

### Prvý migračný skript nesie celý dátový model

`V1__init.sql` zavádza základnú štruktúru aj dátový model — všetky tri tabuľky
(`smb_source`, `media_item`, `scan_run`) vrátane indexov. Ďalšie zmeny schémy
idú do samostatných `V2__…`, `V3__…`; V1 sa už neupravuje.

### Lombok

Pridaný **Lombok** (1.18.46, verziu spravuje Boot BOM). Používa sa striedmo
a len tam, kde ubúda boilerplate:

| Anotácia | Kde |
|---|---|
| `@RequiredArgsConstructor` | služby, repozitáre a controllery s `final` závislosťami |
| `@Slf4j` | namiesto ručného `LoggerFactory.getLogger(...)` |
| `@Getter` / `@Setter` | `SmbSourceForm` (formulár potrebuje settery) a `ScanCounters` |

**DTO a doménové typy ostávajú `record`-y** — `@Data` by tam bol krok späť.

Pozor pri builde: **od JDK 23 sa anotačné procesory nehľadajú na classpath
automaticky.** Lombok musí byť vymenovaný v `annotationProcessorPaths`
maven-compiler-pluginu, inak sa vygenerované metódy jednoducho neobjavia.

### Verzie

| Knižnica | Verzia |
|---|---|
| Spring Boot | 4.1.0 (Spring Framework 7.0.8) |
| smbj | 0.14.0 |
| H2 | 2.4.240 (spravuje BOM) |
| Flyway | 12.4.0 (spravuje BOM) |
| Lombok | 1.18.46 (spravuje BOM) |
| springdoc-openapi | 3.1.0 (rad 3.x je pre Boot 4) |
| WebJars | Bootstrap 5.3.8, jQuery 3.7.1 |

---

## 2026-08-08 — Prihlasovanie a správa používateľov

Server prestal byť otvorený. Pribudli účty, roly a dva oddelené spôsoby prihlásenia.

### Argon2id na heslá aj PINy

Hashuje sa cez `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` — Argon2id,
16 MiB pamäte, 2 iterácie, soľ je súčasťou výsledného reťazca.

**Pozor pri builde:** tento encoder stojí na **BouncyCastle**
(`org.bouncycastle:bcprov-jdk18on`), ktorý Spring Boot BOM **nespravuje** — verziu
treba držať v `pom.xml` ručne (teraz 1.83). Bez neho spadne až runtime.

Spring Security 7.1 ponúka aj `Argon2Password4jPasswordEncoder` (knižnica Password4j),
ale ten by pridal ďalšiu závislosť bez úžitku.

### Dve roly

| Rola | Kam sa dostane |
|---|---|
| `ADMIN` | management UI v prehliadači **aj** Android klient |
| `USER` | **iba** Android klient |

`USER`, ktorý zadá správne heslo do management UI, sa neocitne na 403 — `AccessDeniedHandler`
ho odhlási a vráti na prihlasovaciu stránku s vysvetlením.

### PIN je len pre televízor

Ak má používateľ nastavený PIN (4–8 číslic), prihlási sa ním do Android klienta.
**Do management UI PIN nefunguje**, tam sa vždy vyžaduje plné heslo — inak by správu
servera vrátane SMB credentials chránilo pár číslic. Sedí to na rozhodnutie z 2026-08-05,
že konfigurácia patrí do prehliadača a na diaľkovom ovládači sa heslá píšu zle.

Overovanie je slepé voči tomu, čo klient poslal: `UserService.authenticate` skúsi heslo
a potom PIN. Klient nemusí vedieť, ktoré z toho drží.

### Dva oddelené filter chainy

| Reťazec | Rozsah | Ako sa prihlasuje | Session | CSRF |
|---|---|---|---|---|
| API | `/api/v1/**` | `Authorization: Bearer` | žiadna | vypnuté |
| UI | všetko ostatné | formulár | áno | zapnuté |

**Rozdelenie nie je kozmetické.** Keby session platila aj na `/api/v1/**`, kde je CSRF
vypnuté, cudzia stránka by vedela prehliadaču prihláseného správcu podstrčiť POST.

Dôsledky, ktoré z toho plynú:

- **Dashboard poluje `/admin/sken/stav`, nie `/api/v1/scan/latest`.** Prehliadač so
  session by na bezstavovom API dostal 401. DTO aj služba ostávajú spoločné.
- **Náhľad v knižnici ide cez `/admin/kniznica/{id}/stream`.** Odpoveď skladá rovnaký
  `MediaStreamResponse` ako REST API, takže sa pretáčanie chová rovnako.
- **`/api/openapi` a `/api/swagger-ui.html` vyžadujú ADMIN session** — nie sú pod
  `/api/v1/**`, takže spadajú do UI reťazca. Kontrakt pre klienta nevisí v sieti verejne.

### Token namiesto hesla na televízore

Android klient sa prihlási raz na `POST /api/v1/auth/login` a dostane token, ktorý si
uloží. Heslo ani PIN na zariadení neostávajú.

- Token je 256 bitov zo `SecureRandom`, platnosť 90 dní (`homecenter.security.token-validity`).
- **V databáze je len jeho SHA-256.** Argon2 sa sem nehodí — overuje sa pri každom
  requeste vrátane streamovania a proti hádaniu 256-bitového náhodného čísla pomalý
  hash aj tak nič nerieši.
- Zmena hesla, zmena PINu aj vypnutie účtu **odhlásia všetky zariadenia**. Ide to
  udalosťou `UserCredentialsChangedEvent`, lebo `AuthTokenService` potrebuje
  `UserService` a priame volanie opačným smerom by uzavrelo kruh.
- Zmazanie účtu tokeny zmetie cez `ON DELETE CASCADE`.

### Prvý účet: admin/admin s vynútenou zmenou

Prázdna tabuľka pri štarte znamená, že sa založí `admin` / `admin` s príznakom
`must_change_password`. `PasswordChangeInterceptor` takého správcu nepustí nikam inam
než na `/admin/heslo`.

Príznak sa číta **z databázy, nie z prihlásenej relácie** — v session by po zmene hesla
ostal starý stav a používateľ by uviazol v slučke.

### Poistka na posledného správcu

Posledného zapnutého `ADMIN`a sa nedá zmazať, vypnúť ani preradiť na `USER`
(`LastAdminException`). Bez nej by sa server dal zamknúť tak, že by pomohol len zásah
do databázy.

### Čo ostáva otvorené

- **Heslo k Sambe je v databáze stále v otvorenom tvare.** Účty sú hashované, `smb_source.password`
  nie — server ho potrebuje poslať Sambe. Šifrovanie tohto stĺpca je samostatná úloha.
- **Server beží na HTTP.** V domácej sieti tokeny aj heslá idú po drôte nešifrovane.
  Pred vystavením mimo LAN treba HTTPS.
- **Žiadne obmedzenie počtu pokusov.** Pri 4-číslicovom PINe je to relevantné —
  rate limiting na `/api/v1/auth/login` je ďalší krok.

### Poznámka k Boot 4: Jackson 3

Spring Boot 4.1 prešiel na **Jackson 3** (`tools.jackson.databind`). Bean typu
`com.fasterxml.jackson.databind.ObjectMapper` v kontexte **nie je** — testy, ktoré si ho
pýtali, spadnú na chýbajúcej závislosti. V testoch sa JSON čítal cez JsonPath.

---

## 2026-08-08 — Viacero Samba zdrojov

Zdrojov môže byť nastavených ľubovoľne veľa. Nie je to nová vrstva, skôr dorobenie
niečoho, čo dátový model niesol od začiatku.

### Schéma to uniesla už od V1

`media_item.source_id` aj `scan_run.source_id` existovali od začiatku a unikátny index
`ux_media_item_path` je nad dvojicou **(source_id, relative_path)** — rovnaký film na
dvoch NAS-och sú preto legitímne dve položky. `SmbGateway` už cachoval spojenia v mape
podľa id zdroja a `MediaStreamService` si zdroj hľadal podľa položky, nie „ten jeden“.

Prerobiť sa muselo hlavne to nad tým: UI, sken a filtrovanie.

### `V3__viacero_zdrojov.sql`: pozor na funkcionálne indexy v H2

Prvý pokus bol `CREATE UNIQUE INDEX … ON smb_source (LOWER(name))` a **H2 to nevie** —
`CREATE INDEX` prijíma len názvy stĺpcov, nie výrazy. Migrácia spadla.

Index je preto nad holým `name` a zhodu bez ohľadu na veľkosť písmen kontroluje
`SmbSourceService` dotazom cez `LOWER(...)`. Do `smb_source` zapisuje výhradne tá služba,
takže je to dostatočné; index ostáva poistkou proti presnému duplikátu.

Názov zdroja **musí byť jedinečný** — vyberá sa podľa neho vo filtri knižnice a dva
rovnako pomenované sú na nerozoznanie. Adresa ani share sa nekontrolujú: ten istý server
pripojený na dva rôzne priečinky je legitímne nastavenie.

### Sken prechádza zdroje za sebou, nie paralelne

Domáci NAS nemá dôvod obsluhovať niekoľko súbežných prechodov a sekvenčný priebeh sa dá
v UI zmysluplne zobraziť. Ostáva teda **jeden globálny zámok** (`AtomicBoolean`) — naraz
beží jedna skenovacia úloha, nech pokrýva jeden zdroj alebo päť.

- **Každý zdroj dostane vlastný riadok v `scan_run`.** Počítadlá aj prípadná chyba sa
  tak viažu na konkrétny zdroj.
- **Nedostupný zdroj nezhodí zvyšok.** Zapíše sa mu `FAILED` a pokračuje sa ďalším —
  vypnutý NAS nesmie znamenať, že sa nepreindexuje ani ten druhý. `SmbAccessException`
  sa loguje bez stack trace, je to bežný prevádzkový stav.
- **Riadok v `scan_run` vzniká až vtedy, keď na zdroj príde rad.** Keby sa všetky
  založili dopredu, `findLatest()` by vrátil ten s najvyšším id — teda ten, ktorý sa
  bude skenovať **posledný** — a dashboard by ukazoval nulové počítadlá, kým v skutočnosti
  beží úplne iný zdroj.

Preto `triggerAll` nevracia `ScanRun`, ale `ScanStart` s názvami zdrojov v poradí.
Priebeh si volajúci ťahá z `GET /api/v1/scan/latest`.

### Vypnutý vs. zmazaný zdroj

| Akcia | Čo sa stane s položkami v indexe |
|---|---|
| **Vypnutie** | ostávajú; zdroj sa len nezaraďuje do naplánovaného skenu |
| **Ručný sken vypnutého** | funguje — vypnutie hovorí len o automatike |
| **Zmazanie** | zmiznú spolu s ním (`ON DELETE CASCADE`) |

Vypnutie je zámerne mäkké: keď je NAS na týždeň dole, nemá zmysel prísť o celý index
a po zapnutí ho stavať odznova.

### Dôsledky pre API

- `POST /api/v1/scan` prijíma nepovinné `?sourceId=` a vracia **202 + `ScanStartedDto`**
  namiesto `ScanRunDto` — behy v tej chvíli ešte neexistujú (pozri vyššie).
- `GET /api/v1/media` prijíma `?sourceId=`.
- `MediaItemDto` nesie `sourceId`. Názov zdroja tam **nie je** — vyžadoval by join alebo
  denormalizáciu domény a TV klient ho na tri dlaždice nepotrebuje. Management UI si názvy
  ťahá jedným dotazom (`SmbSourceService.namesById()`) a páruje ich v šablóne.

### UI

`/admin/zdroj` sa zmenilo na `/admin/zdroje` — zoznam v rovnakom duchu ako
`/admin/pouzivatelia`, s formulárom na `/admin/zdroje/{id}`. Pri každom zdroji je vidno
počet položiek, veľkosť a posledný sken, plus tlačidlo na sken samotného zdroja.

Filter zdroja v knižnici a stĺpec „Zdroj“ sa zobrazujú **až pri dvoch a viac zdrojoch** —
pri jedinom by boli len šumom.

---

## 2026-08-08 — Náhľad médií v management UI

V knižnici sa dá pri každej položke otvoriť náhľad: video, obrázok alebo zvuk podľa
kategórie. Slúži na overenie, že sken našiel to, čo mal, a že sa súbor dá zo Samby
naozaj prečítať.

### Jedno okno pre celú tabuľku

Prehrávač je jeden `<div class="modal">` a `homecenter.js` doň podľa kategórie vloží
`<video>`, `<img>` alebo `<audio>`. Údaje idú cez `data-*` atribúty na tlačidle —
`th:on*` Thymeleaf 3.1 nepustí (viď náhľad zdrojov).

Video používa **Video.js 8.23.8** (Apache 2.0). Knižnica pridáva jednotné ovládanie,
klávesnicu, fullscreen, Picture-in-Picture a slovenské texty. Je servírovaná lokálne
cez klasický WebJar; prehliadač ani server pri prehrávaní nepotrebujú internet.
Video.js nemení direct play: zdrojom je stále `/admin/kniznica/{id}/stream` s Range
requestami a samotné dekódovanie robí prehliadač.

Obsah sa nastavuje **až po kliknutí** a pri zatvorení sa `src` odstraňuje plus volá
`load()`. Bez toho by prehliadač ťahal súbor zo Samby ďalej aj po zavretí okna a stovka
riadkov v tabuľke by sa začala sťahovať naraz.

### Formáty, ktoré prehliadač nezvláda

Väčšina bežnej knižnice sa v prehliadači neprehrá — `mkv`, `avi`, `wmv`, `mpg`, `heic`,
`tiff`, `wma`. Server ich **zámerne netranskóduje** (rozhodnutie z 2026-08-05), takže:

- Zjavne nepodporované video kontajnery sa vyradia podľa prípony a ostatné video aj
  audio overí `canPlayType()`. Pri nepodporovanom type sa súbor **ani nezačne
  sťahovať** — ukáže sa hláška a odkaz na stiahnutie.
- `canPlayType()` nevie potvrdiť kodeky vo vnútri kontajnera. Ak napríklad MP4 používa
  kodek, ktorý prehliadač nemá, poistkou je udalosť `error` z Video.js.
- Pri obrázkoch `canPlayType` neexistuje, preto je zoznam overených typov natvrdo;
  poistkou je udalosť `error` na elemente.
- `MediaStreamResponse.ofDownload` posiela `Content-Disposition: attachment`, aby sa
  takýto súbor dal otvoriť v poriadnom prehrávači.

**Neznamená to, že s nimi bude mať problém televízor** — Media3/ExoPlayer `mkv` aj `avi`
zvláda. Je to obmedzenie prehliadača, nie knižnice.

### Médiá nesmú mať `no-store`

Spring Security pridáva do **každej** odpovede `Cache-Control: no-cache, no-store,
max-age=0, must-revalidate`. Pre administračné stránky je to správne, pre médiá nie:
Chrome stavia prehrávanie na multibufferi nad HTTP cache a `no-store` mu berie to,
na čom stojí pretáčanie.

`SecurityConfig` preto vypína predvolený `CacheControlHeadersWriter` a pridáva ho späť
cez `DelegatingRequestMatcherHeaderWriter` s negovaným matcherom na adresy médií.
Tie si hlavičku nastavujú samy (`private, max-age=60`) v `MediaStreamResponse`.

### Čo je overené a čo nie

Serverová strana je overená proti skutočnej Sambe: Range requesty vrátane seeku doprostred
615 MB súboru, správne Content-Type, `inline` vs. `attachment`, ~29 MB/s, magické bajty
sedia. Obrázkový náhľad funguje aj v prehliadači.

**Prehrávanie videa v prehliadači sa overiť nepodarilo** — v automatizovanej relácii Chrome
element `<video>` request vôbec neodošle (v access logu servera nie je, hoci `fetch()` na
tú istú adresu prejde a `<img>` sa načíta). Vyzerá to na obmedzenie toho prostredia, nie
na chybu servera, ale potvrdené to nie je.

---

## 2026-08-08 — Filmové metadáta, žánre a zoskupovanie videí

Filmové informácie sa dopĺňajú cez **TMDb API v3**. Integrácia je voliteľná a
best-effort: SMB index je zdroj pravdy o tom, ktoré súbory existujú, kým TMDb iba
obohacuje už zapísanú položku. Výpadok internetu, limit API ani nenájdený titul
nesmú zhodiť sken alebo odstrániť predtým získané metadáta.

### Konfigurácia a podmienky používania

Server číta TMDb API Read Access Token výhradne z `TMDB_READ_ACCESS_TOKEN` (mapuje sa
na `homecenter.metadata.tmdb-read-access-token`). Prázdna hodnota integráciu vypne;
token sa neukladá do H2, neposiela klientovi a neloguje.

TMDb povoľuje bezplatné API pre nekomerčné použitie s uvedením zdroja. Management UI
preto zobrazuje oficiálne logo a povinný text „This product uses the TMDB API but is
not endorsed or certified by TMDB.“. Podmienky zároveň obmedzujú cache na šesť
mesiacov; uložené výsledky sa obnovujú po **30 dňoch**, chybná požiadavka sa skúsi
znova po jednom dni. Pozri [TMDb API Terms of Use](https://www.themoviedb.org/api-terms-of-use)
a [oficiálnu dokumentáciu](https://developer.themoviedb.org/docs/getting-started).

### Priebeh obohatenia

1. Sken najprv zapíše súbor do `media_item`; sieťová filmová databáza nie je v
   request ceste ani podmienkou existencie položky.
2. `MediaNameParser` odstráni príponu, rok a bežné release značky. Rozpoznáva
   `S01E02`, `1x02` a `Part`/`Pt`/`CD`/`Disc` s číslom.
3. `TmdbMetadataResolver` urobí textové vyhľadanie a následne načíta detail filmu,
   seriálu alebo epizódy. Počas jedného skenu cachuje vyhľadanie seriálu, aby ho
   neopakovalo pri každej epizóde. Požiadavky sú zámerne spomalené o 250 ms.
4. Slovenský text (`sk-SK`) má prednosť; keď popis chýba, načíta sa anglický
   fallback (`en-US`). Plagát sa stiahne do `data/posters`.
5. Pri prvom sieťovom zlyhaní sa ďalšie TMDb volania v danom skene preskočia.
   Ďalší plánovaný alebo ručný sken ich môže skúsiť znovu.

### Dátový model a API

Migrácia `V4__filmove_metadata.sql` rozširuje `media_item` o provider, externé id,
popis, plagát, rok, hodnotenie, typ videa, stav metadát a polia zoskupenia/radenia.
Žánre sú normalizované v `media_genre` a M:N tabuľke `media_item_genre`; nejde o
náhradu troch hlavných kategórií **Videá / Fotky / Hudba**, ale o jemnejšie členenie
vnútri videí.

- `GET /api/v1/media` vracia pri položke `metadata` a prijíma filter `genreId`.
- `GET /api/v1/genres` vracia použité žánre.
- `GET /api/v1/media/{id}/poster` servíruje lokálne uložený plagát.
- Management UI používa ekvivalentný endpoint pod `/admin/**`, nikdy nevolá
  tokenové `/api/v1/**` z prehliadača.

### Zoskupovanie a poradie

Epizódy majú spoločný kľúč seriálu a radia sa podľa `season_number`, potom
`episode_number`. Preto `S01E02` nasleduje pred `S01E10`, aj keď názvy súborov by sa
abecedne mohli správať inak. Filmy z TMDb kolekcie dostanú spoločný kľúč kolekcie;
lokálne očíslované časti dostanú spoločný kľúč odvodený z názvu.

Štruktúra z názvu sa ukladá **aj bez TMDb tokenu**, takže základné zoskupenie seriálov
a viacdielnych videí nezávisí od internetu. Automatické priradenie však nie je
neomylné: nejednoznačné alebo vlastné názvy môžu trafiť nesprávny titul. Manuálne
potvrdenie alebo oprava zhody v UI ostáva samostatná budúca úloha.
