# Technology Decisions

## 2026-08-05—Core stack selection

**Server: Java 25 + Spring Boot 4.1**—REST API + web-based management UI with Thymeleaf
**Client: native Android TV application (Kotlin)**

### Why

- **Virtual threads (Java 21+, including 25) fit the application's core use case
  perfectly.** Streaming from Samba is blocking I/O, and `SmbRandomAccessFile` is a
  blocking API. Virtual threads handle this without exhausting the thread pool—
  enable `spring.threads.virtual.enabled=true`, with no need to tune pool sizes.
- **Java 25 is an LTS release.** Long-term support without repeatedly jumping
  between versions makes sense for a project intended to last for years.
- The considered alternative, **Python + FastAPI, was rejected**—the `smbprotocol`
  library is synchronous and would conflict with FastAPI's async model.
- A future smart assistant does not require Python in the server process—speech,
  language models, and home automation can all be called over HTTP/MQTT from any
  language.

### Server component

| Task | Technology |
|---|---|
| Language | Java 25 (LTS) |
| Framework | Spring Boot 4.1 (Spring Framework 7, Jakarta EE 11) |
| Concurrency | virtual threads—`spring.threads.virtual.enabled=true` |
| Management UI | Thymeleaf (server-side rendering, native to Spring Boot) |
| DTOs | Java `record` |
| Build | Maven, compiled with JDK 25 |
| SMB access | smbj (SMB2/3), positional reads through `share.File` |
| Media index | H2 + Flyway (originally SQLite, changed on 2026-08-06) |
| File metadata | ffprobe (FFmpeg) |
| Movie metadata | TMDb API |
| Photo thumbnails | thumbnailator |
| Deployment | Docker + docker-compose |

### Management UI (Thymeleaf)

| Task | Technology |
|---|---|
| CSS framework | Bootstrap 5 |
| JavaScript | jQuery |
| Browser video | Video.js through a WebJar |
| Charts | Chart.js—to be added only when needed |
| Colour, shape, icons | own token layer over Bootstrap—[design-system.md](design-system.md) |

Libraries are served **locally through WebJars**, not from a CDN. The server runs
on a home network, and the management UI must work without internet access.

### Client component

| Task | Technology |
|---|---|
| UI | Jetpack Compose for TV (`androidx.tv:tv-material`)—not Leanback |
| Player | Media3 / ExoPlayer |
| REST | Retrofit + OkHttp + kotlinx.serialization |
| Images | Coil |
| Dependency injection | Hilt |
| Offline cache | Room |

### Resulting principles

1. **The server is the only component that knows the SMB credentials.** The client
   does not access Samba directly; the server proxies files over HTTP with Range
   requests.
2. **Samba is not scanned on every request.** Scanning runs in the background on a
   schedule and on manual request; the REST API reads from the local index.
3. **No transcoding for now.** The server only forwards files (direct play). FFmpeg
   transcoding will be addressed when a specific incompatible file requires it.
4. **SMB source configuration belongs in the server's Thymeleaf UI, not in the TV
   app.** Entering network paths and passwords with a D-pad is painful. The TV keeps
   three tiles: Videos / Photos / Music.
5. **The Thymeleaf UI and REST API are separate layers over the same service layer.**
   Admin controllers return HTML, and API controllers return JSON—neither contains
   logic unavailable to the other.
6. **The server uses Java and the client uses Kotlin—models are not shared.** Their
   contract is the OpenAPI specification (springdoc), from which the client API can
   be generated. Kotlin consumes JSON from Java records without problems, but the
   contract must be maintained in one place instead of being duplicated manually
   on both sides.

### Open questions

- What hardware the server will run on. On a Raspberry Pi, the JVM's baseline
  memory use of approximately 300 MB is noticeable—the likely solution is heap
  tuning or possibly a GraalVM native image rather than changing the framework.

---

## 2026-08-06—Server foundation

The base package is `org.javerland.homecenter`. The `backend/` module was created
with a functional skeleton: media indexing, Samba scanning, a REST API, streaming
with Range requests, and a Thymeleaf management UI.

### Build: Maven

**Maven was selected**, not Gradle. The reason is practical: the machine has
Gradle 6.4.1, which is far from supporting Java 25, while Maven 3.9.6 runs with it
without problems. Maven also has no daemon that would need tuning alongside Spring.

Project version: `org.javerland:homecenter:0.1.0-SNAPSHOT`.

### Resolved open questions

**Thymeleaf works with Spring Framework 7.** The `thymeleaf-spring7` artifact does
not exist and will not be created—Spring Boot 4.1 uses `thymeleaf-spring6`
(Thymeleaf 3.1.5.RELEASE), which is compatible with Spring 7. It is pulled in by
`spring-boot-starter-thymeleaf` through the `spring-boot-thymeleaf` module, so
nothing needs to be added manually.

### Corrections to the original notes

**smbj does not have `SmbRandomAccessFile`.** That class belongs to jcifs-ng.
Random access in smbj is provided by `com.hierynomus.smbj.share.File`:

```java
int read(byte[] buffer, long fileOffset, int bufferOffset, int length)
```

Reading from an absolute position is exactly what seeking requires, so the server
has no reason to use jcifs-ng. `SmbInputStream` is built on this call, and its
`skip()` operation moves the position in O(1) without transferring bytes.

### Spring Boot 4 is split into modules

Unlike Boot 3, the old artifacts are not sufficient because auto-configurations
have moved:

| Feature | Location in Boot 4 |
|---|---|
| Flyway auto-configuration | `org.springframework.boot:spring-boot-flyway` (`flyway-core` alone does not run migrations) |
| `@WebMvcTest` | `org.springframework.boot:spring-boot-starter-webmvc-test`, package `org.springframework.boot.webmvc.test.autoconfigure` |
| Thymeleaf integration | `spring-boot-thymeleaf` → `thymeleaf-spring6` |

`flyway-core` includes H2 support, so no separate module is needed.

### Database access: JdbcClient, not JPA

The index is read and written through Spring's **`JdbcClient`**, with manual
`RowMapper` implementations. For a few index tables, an ORM is an unnecessary
layer, and Flyway manages the schema anyway.

### SMB source configuration is stored in the database

The source is configured at runtime through the management UI, not in
`application.yml`; otherwise, changing a password would require restarting the
server. The data is stored in the `smb_source` table, and the password never
appears in DTOs, templates, or `toString()`.

**Open:** the password is stored in plaintext in the database. This is acceptable
for now on a server in a home network, but encryption of sensitive columns must be
addressed before the media center is exposed outside the LAN.

---

## 2026-08-06—H2 instead of SQLite, Lombok added

### The index runs on H2

**Change from the original notes:** the index does not use SQLite; it uses **H2** in
file mode (`jdbc:h2:file:./data/homecenter`, file `data/homecenter.mv.db`). Flyway
remains in use.

Benefits:

- **Native data types.** `TIMESTAMP WITH TIME ZONE` and `BOOLEAN` replace text and
  `INTEGER 0/1`. This removed all the workarounds around time formatting—SQLite has
  no date type, so times had to be stored as fixed-length strings to remain sortable
  in SQL.
- **Standard SQL.** The migration uses `GENERATED BY DEFAULT AS IDENTITY`, not
  SQLite's `AUTOINCREMENT`. A possible migration to PostgreSQL would require fewer
  changes.
- **Flyway without an extra module**—H2 support is included in `flyway-core`.
- No native library; `sqlite-jdbc` carries a platform-specific binary.

The tradeoff is that the H2 file format has changed incompatibly between major
versions in the past (1.4 → 2.x). When upgrading H2, assume that the existing file
may not open. That is acceptable for this project because the index contains
derived data; after deleting `data/`, the next scan rebuilds it. This does not apply
to any data added to the database later that cannot be reconstructed.

H2 does not support SQLite's `ORDER BY ... COLLATE NOCASE`; library listings are
sorted with `LOWER(title)`.

### The first migration script contains the complete data model

`V1__init.sql` creates the base structure and data model—all three tables
(`smb_source`, `media_item`, and `scan_run`), including indexes. Further schema
changes go into separate `V2__…`, `V3__…` migrations; V1 is no longer modified.

### Lombok

**Lombok** was added (1.18.46, with the version managed by the Boot BOM). It is used
sparingly and only where it reduces boilerplate:

| Annotation | Used for |
|---|---|
| `@RequiredArgsConstructor` | services, repositories, and controllers with `final` dependencies |
| `@Slf4j` | instead of manual `LoggerFactory.getLogger(...)` calls |
| `@Getter` / `@Setter` | `SmbSourceForm` (the form requires setters) and `ScanCounters` |

**DTOs and domain types remain records**—using `@Data` there would be a step
backward.

Build warning: **since JDK 23, annotation processors are not discovered on the
classpath automatically.** Lombok must be listed in the Maven Compiler Plugin's
`annotationProcessorPaths`, or its generated methods will simply not appear.

### Versions

| Library | Version |
|---|---|
| Spring Boot | 4.1.0 (Spring Framework 7.0.8) |
| smbj | 0.14.0 |
| H2 | 2.4.240 (managed by the BOM) |
| Flyway | 12.4.0 (managed by the BOM) |
| Lombok | 1.18.46 (managed by the BOM) |
| springdoc-openapi | 3.1.0 (the 3.x line is for Boot 4) |
| WebJars | Bootstrap 5.3.8, jQuery 3.7.1 |

---

## 2026-08-08—Authentication and user management

The server is no longer open. Accounts, roles, and two separate authentication
methods were added.

### Argon2id for passwords and PINs

Hashing uses `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()`—Argon2id,
16 MiB of memory, two iterations, with the salt included in the resulting string.

**Build warning:** this encoder relies on **BouncyCastle**
(`org.bouncycastle:bcprov-jdk18on`), whose version is **not managed** by the Spring
Boot BOM. The version must be maintained manually in `pom.xml` (currently 1.83).
Without it, the application fails only at runtime.

Spring Security 7.1 also provides `Argon2Password4jPasswordEncoder` (from the
Password4j library), but it would add another dependency without providing a
benefit.

### Two roles

| Role | Access |
|---|---|
| `ADMIN` | browser-based management UI **and** Android client |
| `USER` | Android client **only** |

A `USER` who enters the correct password in the management UI does not end up on
a 403 page—the `AccessDeniedHandler` logs them out and returns them to the login
page with an explanation.

### The PIN is only for the TV

If a user has a PIN set (4–8 digits), they can use it to log in to the Android
client. **The PIN does not work in the management UI**, which always requires the
full password; otherwise, a few digits would protect server management, including
the SMB credentials. This follows the 2026-08-05 decision that configuration
belongs in the browser and passwords are difficult to enter with a remote control.

Verification is agnostic about what the client sent: `UserService.authenticate`
tries the password and then the PIN. The client does not need to know which one it
holds.

### Two separate filter chains

| Chain | Scope | Authentication | Session | CSRF |
|---|---|---|---|---|
| API | `/api/v1/**` | `Authorization: Bearer` | none | disabled |
| UI | everything else | form | yes | enabled |

**The separation is not cosmetic.** If the session also applied to `/api/v1/**`,
where CSRF is disabled, a malicious website could make a logged-in administrator's
browser submit a POST request.

Resulting consequences:

- **The dashboard polls `/admin/sken/stav`, not `/api/v1/scan/latest`.** A browser
  with a session would receive 401 from the stateless API. The DTO and service
  remain shared.
- **Library previews use `/admin/kniznica/{id}/stream`.** The response is assembled
  with the same `MediaStreamResponse` as the REST API, so seeking behaves identically.
- **`/api/openapi` and `/api/swagger-ui.html` require an ADMIN session**—they are not
  under `/api/v1/**`, so they belong to the UI chain. The client contract is not
  publicly exposed on the network.

### A token instead of a password on the TV

The Android client logs in once through `POST /api/v1/auth/login` and receives a
token, which it stores. Neither the password nor the PIN remains on the device.

- The token is 256 bits from `SecureRandom` and is valid for 90 days
  (`homecenter.security.token-validity`).
- **Only its SHA-256 hash is stored in the database.** Argon2 is unsuitable here—
  the token is verified on every request, including streaming requests, and a slow
  hash adds no protection against guessing a random 256-bit number.
- Changing the password or PIN, or disabling the account, **logs out all devices**.
  This uses a `UserCredentialsChangedEvent` because `AuthTokenService` depends on
  `UserService`, and a direct call in the opposite direction would create a cycle.
- Deleting an account removes its tokens through `ON DELETE CASCADE`.

### Initial account: admin/admin with a forced password change

An empty user table at startup causes the `admin` / `admin` account to be created
with the `must_change_password` flag. `PasswordChangeInterceptor` prevents that
administrator from accessing anything except `/admin/heslo`.

The flag is read **from the database, not from the authenticated session**—if kept
in the session, the old value would remain after a password change and trap the
user in a loop.

### Safeguard for the last administrator

The last enabled `ADMIN` cannot be deleted, disabled, or changed to `USER`
(`LastAdminException`). Without this safeguard, the server could be locked in a
state recoverable only by modifying the database.

### What remains open

- **The Samba password is still stored in plaintext in the database.** Account
  passwords are hashed, but `smb_source.password` is not—the server needs to send
  it to Samba. Encrypting this column is a separate task.
- **The server runs over HTTP.** On a home network, tokens and passwords travel over
  the wire unencrypted. HTTPS is required before exposing the server outside the
  LAN.
- **There is no login attempt limit.** This matters for a four-digit PIN; rate
  limiting on `/api/v1/auth/login` is a future step.

### Boot 4 note: Jackson 3

Spring Boot 4.1 moved to **Jackson 3** (`tools.jackson.databind`). There is no bean
of type `com.fasterxml.jackson.databind.ObjectMapper` in the context—tests that
requested one failed because the dependency was missing. Tests used JsonPath to
read JSON instead.

---

## 2026-08-08—Multiple Samba sources

Any number of sources can be configured. This is not a new layer so much as the
completion of something the data model supported from the beginning.

### The schema supported it from V1

Both `media_item.source_id` and `scan_run.source_id` existed from the beginning,
and the unique index `ux_media_item_path` covers the pair **(source_id,
relative_path)**, so the same movie on two NAS devices is legitimately represented
as two items. `SmbGateway` already cached connections in a map by source ID, and
`MediaStreamService` looked up the source through the item rather than assuming
"the one" source.

The UI, scanning, and filtering were the main parts that needed changes.

### `V3__viacero_zdrojov.sql`: beware of functional indexes in H2

The first attempt used `CREATE UNIQUE INDEX … ON smb_source (LOWER(name))`, which
**H2 does not support**—`CREATE INDEX` accepts column names only, not expressions.
The migration failed.

The index therefore covers the plain `name` column, while `SmbSourceService` checks
for case-insensitive matches with a `LOWER(...)` query. Only that service writes to
`smb_source`, so this is sufficient; the index remains a safeguard against exact
duplicates.

The source name **must be unique**—the library filter identifies sources by name,
and two identically named sources would be indistinguishable. The address and share
are not checked: connecting the same server to two different directories is a valid
configuration.

### A scan processes sources sequentially, not in parallel

A home NAS has no reason to serve several concurrent traversals, and sequential
progress can be represented meaningfully in the UI. There is therefore still **one
global lock** (`AtomicBoolean`): only one scanning task runs at a time, whether it
covers one source or five.

- **Each source gets its own row in `scan_run`.** Counters and any error are thus
  associated with a specific source.
- **An unavailable source does not stop the rest.** It is marked `FAILED`, and the
  scan continues with the next source—an offline NAS must not prevent another one
  from being reindexed. `SmbAccessException` is logged without a stack trace because
  this is an ordinary operating condition.
- **A row is created in `scan_run` only when its source's turn begins.** If all rows
  were created in advance, `findLatest()` would return the one with the highest ID—
  the source that will be scanned **last**—and the dashboard would display zero
  counters while an entirely different source was actually being scanned.

For this reason, `triggerAll` returns a `ScanStart` with source names in order, not
a `ScanRun`. The caller retrieves progress from `GET /api/v1/scan/latest`.

### Disabled versus deleted sources

| Action | What happens to indexed items |
|---|---|
| **Disable** | items remain; the source is only excluded from scheduled scans |
| **Manual scan of a disabled source** | works—disabling applies only to automation |
| **Delete** | items are removed with the source (`ON DELETE CASCADE`) |

Disabling is intentionally soft: if a NAS is offline for a week, there is no reason
to lose the entire index and rebuild it after the NAS is turned on again.

### API consequences

- `POST /api/v1/scan` accepts an optional `?sourceId=` and returns **202 +
  `ScanStartedDto`** instead of `ScanRunDto`—the runs do not exist yet at that point
  (see above).
- `GET /api/v1/media` accepts `?sourceId=`.
- `MediaItemDto` carries `sourceId`. The source name is **not included** because it
  would require a join or domain denormalization, and the TV client does not need it
  for the three tiles. The management UI fetches names in one query
  (`SmbSourceService.namesById()`) and pairs them in the template.

### UI

`/admin/zdroj` changed to `/admin/zdroje`—a list in the same style as
`/admin/pouzivatelia`, with a form at `/admin/zdroje/{id}`. Each source shows its
item count, size, and latest scan, together with a button to scan that source alone.

The library source filter and "Source" column appear **only when two or more sources
exist**—with a single source, they would only add noise.

---

## 2026-08-08—Media previews in the management UI

Each library item can open a preview: video, image, or audio according to its
category. This verifies that the scan found the intended content and that the file
can actually be read from Samba.

### One dialog for the entire table

The player is a single `<div class="modal">`, and `homecenter.js` inserts a
`<video>`, `<img>`, or `<audio>` element according to the category. Data is passed
through `data-*` attributes on the button—Thymeleaf 3.1 does not allow `th:on*`
(see the source preview implementation).

Video uses **Video.js 8.23.8** (Apache 2.0). The library provides consistent
controls, keyboard support, fullscreen, Picture-in-Picture, and Slovak localization
strings. It is served locally through a classic WebJar; neither the browser nor the
server requires internet access during playback. Video.js does not change direct
play: the source remains `/admin/kniznica/{id}/stream` with Range requests, and the
browser performs the decoding.

Content is assigned **only after a click**; when the dialog closes, `src` is removed
and `load()` is called. Otherwise, the browser would keep reading the file from
Samba after the dialog closed, and a hundred rows in the table would begin
downloading at once.

### Formats the browser cannot handle

Much of a typical library cannot be played in the browser—`mkv`, `avi`, `wmv`,
`mpg`, `heic`, `tiff`, and `wma`. The server **intentionally does not transcode**
them (the 2026-08-05 decision), so:

- Clearly unsupported video containers are rejected by extension, while
  `canPlayType()` checks the remaining video and audio. For an unsupported type,
  the file **does not even begin downloading**; a message and download link appear
  instead.
- `canPlayType()` cannot confirm the codecs inside a container. If, for example, an
  MP4 uses a codec unavailable in the browser, the Video.js `error` event is the
  fallback.
- Images have no `canPlayType`, so the list of verified types is hard-coded; the
  element's `error` event is the fallback.
- `MediaStreamResponse.ofDownload` sends `Content-Disposition: attachment` so such
  a file can be opened in a capable player.

**This does not mean the TV will have trouble with these formats**—Media3/ExoPlayer
supports both `mkv` and `avi`. This is a browser limitation, not a library
limitation.

### Media responses must not use `no-store`

Spring Security adds `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`
to **every** response. This is correct for administration pages but not for media:
Chrome builds playback on multiple buffers over the HTTP cache, and `no-store`
removes the foundation that seeking relies on.

`SecurityConfig` therefore disables the default `CacheControlHeadersWriter` and
adds it back through a `DelegatingRequestMatcherHeaderWriter` with a negated matcher
for media addresses. Media responses set their own header (`private, max-age=60`)
in `MediaStreamResponse`.

### What has and has not been verified

The server side has been verified against real Samba storage: Range requests,
including a seek into the middle of a 615 MB file, correct Content-Type, `inline`
versus `attachment`, approximately 29 MB/s, and matching magic bytes. Image previews
also work in the browser.

**Browser video playback could not be verified**—in an automated Chrome session,
the `<video>` element did not send a request at all (it does not appear in the
server access log, although `fetch()` to the same address succeeds and `<img>`
loads). This appears to be a limitation of that environment rather than a server
bug, but it has not been confirmed.

---

## 2026-08-08—Movie metadata, genres, and video grouping

Movie information is enriched through the **TMDb API v3**. The integration is
optional and best-effort: the SMB index is the source of truth about which files
exist, while TMDb only enriches an item that has already been indexed. An internet
outage, API rate limit, or missing title must not stop a scan or remove metadata
obtained previously.

### Configuration and terms of use

The server reads the TMDb API Read Access Token only from
`TMDB_READ_ACCESS_TOKEN` (mapped to
`homecenter.metadata.tmdb-read-access-token`). An empty value disables the
integration; the token is not stored in H2, sent to the client, or logged.

TMDb permits free API use for noncommercial purposes with attribution. The
management UI therefore displays the official logo and the required text: "This
product uses the TMDB API but is not endorsed or certified by TMDB." The terms also
limit caching to six months; stored results are refreshed after **30 days**, and a
failed request is retried after one day. See the
[TMDb API Terms of Use](https://www.themoviedb.org/api-terms-of-use) and the
[official documentation](https://developer.themoviedb.org/docs/getting-started).

### Enrichment flow

1. The scan first writes the file to `media_item`; the online movie database is
   neither in the request path nor a condition for the item's existence.
2. `MediaNameParser` removes the extension, year, and common release tags. It
   recognizes `S01E02`, `1x02`, and numbered `Part`/`Pt`/`CD`/`Disc` markers.
3. `TmdbMetadataResolver` performs a text search and then loads movie, series, or
   episode details. During one scan, it caches the series search to avoid repeating
   it for every episode. Requests are intentionally delayed by 250 ms.
4. Slovak text (`sk-SK`) takes precedence; when the description is missing, the
   English fallback (`en-US`) is loaded. The poster is downloaded to `data/posters`.
5. After the first network failure, further TMDb calls are skipped for that scan.
   The next scheduled or manual scan can try them again.

### Data model and API

Migration `V4__filmove_metadata.sql` extends `media_item` with the provider,
external ID, description, poster, year, rating, video type, metadata status, and
grouping/sorting fields. Genres are normalized in `media_genre` and the many-to-many
`media_item_genre` table; they do not replace the three main categories of
**Videos / Photos / Music**, but provide finer classification within videos.

- `GET /api/v1/media` returns `metadata` with each item and accepts the `genreId`
  filter.
- `GET /api/v1/genres` returns the genres in use.
- `GET /api/v1/media/{id}/poster` serves the locally stored poster.
- The management UI uses an equivalent endpoint under `/admin/**`; it never calls
  token-protected `/api/v1/**` endpoints from the browser.

### Grouping and order

Episodes share a series key and are ordered by `season_number`, then
`episode_number`. Therefore, `S01E02` comes before `S01E10`, even though the
filenames might sort differently alphabetically. Movies in a TMDb collection share
a collection key; locally numbered parts share a key derived from the title.

Filename structure is stored **even without a TMDb token**, so basic grouping of
series and multipart videos does not depend on internet access. Automatic matching
is not infallible, however: ambiguous or custom names may match the wrong title.
Manually confirming or correcting a match in the UI remains a separate future task.

---

## 2026-08-08—Management UI design system

The UI was plain Bootstrap with about a hundred lines of patch CSS. It worked,
but it looked like an unstyled admin scaffold and there was no answer to "what
colour is this supposed to be." A design foundation was introduced, documented
in full in **[design-system.md](design-system.md)**; only the decisions are
recorded here.

### Three token layers, not Bootstrap overrides

The obvious approach—override `--bs-primary` and stop—breaks as soon as a second
theme exists. `--bs-primary` records what Bootstrap paints with, never what a
colour *means*, so there is nowhere to say that a running scan and a live stream
are the same idea.

Instead: `tokens.css` defines raw ramps (`--hc-iris-600`), maps them to roles per
theme (`--hc-brand`, `--hc-surface`), and only then forwards the roles into
`--bs-*`. Because the last step exists, **plain Bootstrap markup in the templates
follows the theme with no extra classes**—a bare `<div class="card">` or
`<table class="table">` is already themed. Component CSS is forbidden from naming
a palette step; that rule is what makes one theme switch work.

Palette: **Iris** (indigo-violet) for the brand, **Aqua** for activity and
streaming, a blue-tinted **Slate** for every surface, and green/amber/rose for
outcomes. The three TV categories keep one colour each—Videos Iris, Photos Aqua,
Music Amber—across the tile, its icon, the library badge and the poster
placeholder.

### Light and dark, with dark as a first-class theme

Bootstrap 5.3's `data-bs-theme` drives it. The stored choice has three states,
because "auto" has to stay reachable—a user who once clicked the toggle must be
able to hand control back to the operating system.

**The theme is applied by an inline script in `<head>`, not by `homecenter.js`.**
Waiting for the external file means every page load flashes the light theme
first. `homecenter.js` owns only the menu. The consequence is that the "is it
dark?" decision exists twice and the two copies must be changed together.

The dark theme is not the light theme inverted: surfaces get lighter as they
rise (a shadow cannot darken an already dark surface), brand and status colours
move two to three steps up the ramp, and solid pale tints become 14–16 % alpha.

### Icons as CSS masks

26 icons live in `icons.css` as inline SVG data URIs used as `mask-image`. No
icon-font WebJar to add, nothing to download on a network that may be offline,
and—because a mask is painted with `background-color`—every icon inherits
`currentColor` and tints itself correctly in both themes.

Watch out: an `.hc-i` with no variant class renders as a **solid square**, since
`mask-image: none` means unmasked rather than empty.

### Two things that cost time

- Bootstrap's `--bs-*-rgb` variables hold a **comma-separated** triplet. Alpha
  must be `rgba(var(--bs-primary-rgb), .3)`; the modern `rgb(... / .3)` slash
  form cannot be mixed with commas and the browser silently drops the entire
  declaration. Five rules were written this way and failed invisibly.
- **Thymeleaf parses HTML comments.** A comment containing a double-bracket
  sequence is treated as an inline expression and the template fails to parse at
  render time—not at build time. Prose in comments must avoid it.

`/img/**` was added to the anonymous allowlist in `SecurityConfig`; the favicon
is referenced from the login page, which is reached before authentication.

---

## 2026-08-08—Android TV client

`frontend/` is no longer empty. The client covers the whole path a household
actually walks: enter the server address, sign in, browse the three categories,
open a video and watch it, look through photos, listen to music, sign out.

### The client API is generated, never written

The OpenAPI specification was already declared the contract between the Java
server and the Kotlin client. It is now enforced rather than described:
`frontend/openapi/homecenter-openapi.json` is a committed export of
`GET /api/openapi`, and the **openapi-generator** Gradle plugin turns it into
Kotlin models and Retrofit interfaces during the build. No DTO is written twice.

Refreshing the snapshot needs a running server and an administrator account,
because `/api/openapi` belongs to the management UI's filter chain—
`frontend/openapi/refresh.ps1` does the form login and the export.

Three things had to change on the server for this to work:

- **`springdoc.paths-to-match: /api/v1/**`.** The specification described the
  `/admin/**` endpoints too, and generating a Kotlin API for them would suggest
  the TV may call them. It may not; that is architectural rule 9.
- **The licence needed an SPDX identifier.** OpenAPI 3.1 rejects a licence
  carrying only a name, and the generator validates before it reads anything.
  `Domáce použitie` became `Apache-2.0`, which is the repository's actual licence.
- **Tag names lost their diacritics.** The generator turns each tag into a Kotlin
  interface name and silently drops what it cannot spell, so `Knižnica` arrived as
  `KninicaApi`. The tags are now `Kniznica`, `Prihlasenie`, `Prehravanie`—the same
  convention the URLs already follow. Descriptions are prose and kept theirs.

Generated models are all-nullable, because springdoc marks almost nothing
required. They therefore stop at the repository layer, which maps them to domain
types that state what is actually there. `dateLibrary=string` keeps instants as
strings: kotlinx.serialization has no serializer for `java.time`, and the client
only ever formats them for display.

### Build: AGP 9 and JDK 21

The client cannot be built with the JDK the server needs. **AGP does not run on
JDK 25**, so `frontend/` is built with **JDK 21** while `backend/` stays on 25.

The version choice was forced from below rather than picked. AndroidX releases
from mid-2026 (`core-ktx` 1.19, `lifecycle` 2.11) refuse to be consumed by
anything under AGP 9.1 and `compileSdk` 37, and Hilt 2.59+ refuses to apply on
anything under AGP 9. The result is AGP 9.3.1 on Gradle 9.7, `compileSdk` and
`targetSdk` 37, `minSdk` 23.

**AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android`
alongside it is an error; the Compose and serialization compiler plugins are
still applied normally, `jvmTarget` follows
`android.compileOptions.targetCompatibility`, and generated sources are added
through `android.sourceSets` rather than the Kotlin extension.

One trap: inside an `openApiGenerate { }` block, `library` resolves to something
else in this build and the assignment does not compile. The extension is
configured through `extensions.getByType(...)` instead.

### How the client reaches the server

The server address is typed on the television, so Retrofit cannot be given a base
URL when it is built. Every call is issued against a placeholder host and pointed
at the real one by an interceptor, which also attaches the bearer token—to
everything except login, the one endpoint that does not have one yet.

That interceptor is the single place authentication happens, which is why
**Coil and ExoPlayer both use the same OkHttp client**. Posters and streams are
not public; Coil's own client would collect a 401 on every poster, and
ExoPlayer's default data source on every film.

A 401 on anything else means the token died—the server invalidates all of them
when a password or PIN changes. The interceptor clears it and the navigation host
returns to login.

### What the client stores

Only three things: the server address, the token, and where each video was left
off. The password and PIN are never written down; that is the whole point of the
token. Backups are switched off, because a token restored onto a different
television would be a session nobody signed in for.

Resume positions are the one piece of state the server does not have—it indexes
files and has no idea who watched what. They are written every ten seconds rather
than only on exit, since switching a television off mid-film is an ordinary way to
stop watching. A position under 30 seconds or within a minute of the end is
discarded: "continue" should not land on the opening titles or the closing credits.

### Screens, and what is deliberately missing

Videos get a detail screen; photos and music do not, because making somebody press
OK twice to look at a picture is one press too many. Episodes of a series are
grouped on the detail screen using the server's season and episode numbers, which
is what stops `S01E10` from sorting before `S01E02`.

**There is no way to configure anything from the TV.** No sources, no users, no
scan trigger—the 2026-08-05 decision holds. Settings shows the account, the server
and the way out, and points at the browser for the rest.

Compose for TV supplies no text field, so the two screens that need typing borrow
the ordinary Material 3 one, wrapped in the project's colours. The player is
Media3's own `PlayerView` inside an `AndroidView`: it already handles a D-pad and
a seek bar the way a remote expects, and rebuilding that in Compose would mean
rebuilding its focus rules too.

The palette is the design system's, dark only. A television in a living room is
looked at from three metres away in the evening, and the design system already
treats dark as a first-class theme rather than an inversion.

### What has not been verified

The app **builds, passes its unit tests and lints clean, but has never been run**.
This machine has no Android TV system image and no `cmdline-tools` to install one,
so nothing here has been exercised against a real server: the login round trip,
poster loading, seeking, and D-pad focus order are all unproven.
