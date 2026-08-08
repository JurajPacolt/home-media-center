# AGENTS.md

Instructions for AI agents working in this repository.

## About the project

Home media center: a Spring Boot server that indexes media from Samba storage
and streams it to a native Android TV client. In addition to the REST API, the
server also provides a web-based management UI. The project may be extended
with a smart home assistant in the future—the architecture must accommodate
this, but it is not being implemented yet.

The documentation is written in English.

## Diagram convention

Draw documentation diagrams in **Mermaid** (` ```mermaid ` blocks), not as ASCII
art. This applies to the README, `doc/`, and any other `.md` files.

## Status and build

**The backend has a functional skeleton**—media indexing, Samba scanning, TMDb
movie metadata, a REST API, streaming with Range request support, a Thymeleaf UI,
and authentication with user management. **`frontend/` is still empty**; work on
the Android TV client has not started yet.

On first launch, the server creates the **`admin` / `admin`** administrator account
and forces a password change.

The build uses **Maven** (not Gradle) and requires **JDK 25**. The machine's default
`JAVA_HOME` points to an older Java version, so it must be changed:

```powershell
$env:JAVA_HOME = "d:\java\jdk-25"
cd backend
```

| Command | What it does |
|---|---|
| `mvn test` | runs all tests |
| `mvn "-Dtest=ByteRangeTest" test` | runs one test class |
| `mvn "-Dtest=ByteRangeTest#uzavretyRozsah" test` | runs one test |
| `mvn spring-boot:run` | starts the server at <http://localhost:8085/admin> |
| `mvn package` | creates an executable JAR in `target/` |

In PowerShell, `-D` parameters must be quoted; otherwise, the shell breaks them
apart.

At startup, the server creates `backend/data/homecenter.mv.db` (the H2 index).
The `data/` directory is in `.gitignore` and can safely be deleted—it will be
recreated, and only the index is lost; the next scan will rebuild it.

The project uses **Lombok**. If getters or `log` suddenly "disappear" after a
change to `pom.xml`, check `annotationProcessorPaths` in the Maven Compiler
Plugin—since JDK 23, processors are no longer discovered on the classpath
automatically.

## Decisions that are closed

Do not reopen these decisions or suggest alternatives unless the user explicitly
asks you to:

| Layer | Choice |
|---|---|
| Server | **Java 25 (LTS) + Spring Boot 4.1** |
| Management UI | **Thymeleaf + Bootstrap 5 + jQuery**, charts with **Chart.js** |
| Client | **Android TV app in Kotlin** (Compose for TV, Media3) |
| Authentication | **Argon2id** for passwords and PINs, **Bearer token** for the client |

Considered and **rejected**: Python + FastAPI, Kotlin on the server, and basing the
project on Jellyfin.

For the rationale and library list, see
[doc/implementation-plan.md](doc/implementation-plan.md). That file is the source
of truth—update it when making further decisions.

## Architectural constraints

These rules are not apparent from any single source file:

1. **The server is the sole holder of the SMB credentials.** The client never
   accesses Samba directly. The server proxies files over HTTP and **must** support
   Range requests—video seeking does not work without them.

2. **Blocking I/O is intentional, not a flaw.** smbj is a blocking API and runs on
   virtual threads (`spring.threads.virtual.enabled=true`). Do not rewrite streaming
   as a reactive model or introduce WebFlux—the choice of Java 25 relies on this
   design.

   Positional reads use `com.hierynomus.smbj.share.File`:
   `read(byte[] buffer, long fileOffset, int bufferOffset, int length)`.
   `SmbRandomAccessFile` is a **jcifs-ng** class, not an smbj class—do not look for
   it here.

3. **Index first.** Samba is not scanned while handling requests. The REST API
   reads from the H2 index; scanning runs in the background on a schedule and can
   also be triggered manually. Scanning in the request path is a regression.

   **Multiple sources can be configured.** Never assume there is "the one" source—
   every indexed item carries its `source_id` and is streamed from that source.
   A scan processes sources **sequentially in a single task**, and each source gets
   its own row in `scan_run`; an unavailable source is marked `FAILED`, and the scan
   continues with the next source.

4. **No transcoding until a specific file proves it necessary.** Direct play is
   the default—the server only forwards the file. FFmpeg is introduced only for
   demonstrably incompatible content.

   The management UI preview follows this rule: content the browser cannot handle
   natively (`mkv`, `avi`, `wmv`, `heic`, `wma`) is not downloaded at all—a message
   and a download link are shown instead. **Do not confuse this with what the TV
   can handle**—Media3/ExoPlayer plays both `mkv` and `avi` without problems.

   File responses also **must not include `Cache-Control: no-store`**, which Spring
   Security adds everywhere else. Chrome builds playback on multiple buffers over
   the HTTP cache, and seeking stops working without that cache. An exception in
   `SecurityConfig` and a custom header in `MediaStreamResponse` handle this.

5. **Thymeleaf and REST are two thin layers over a shared service layer.** Admin
   controllers return HTML, and API controllers return JSON. Scanning, indexing,
   and Samba access logic belongs in services, not controllers; otherwise, it will
   be duplicated between the UI and API.

6. **The OpenAPI specification is the contract between server and client**
   (springdoc). The server uses Java, the client uses Kotlin, and models are not
   shared. Do not manually duplicate DTOs on both sides.

7. **The schema changes only through Flyway migrations.** `V1__init.sql` contains
   the base structure and data model and **must no longer be edited**—every further
   change is a new `V2__…`, `V3__…` script. Rewriting an existing migration breaks
   its checksum on every deployment where it has already run.

8. **Management UI frontend libraries are served locally through WebJars.** Do not
   use `<script src="https://cdn...">`—the server runs on a home network, and the UI
   must work without internet access. Do not introduce a build step (npm, bundler)
   or an SPA framework into the management UI; Thymeleaf renders HTML on the server,
   and jQuery enhances it.

9. **Authentication has two separate filter chains, and they must not be merged.**
   `/api/v1/**` is stateless, accepts only `Authorization: Bearer`, and has CSRF
   disabled. Everything else uses a form-based session with CSRF and the `ADMIN`
   role. If the session also applied to the API, a malicious site could make a
   logged-in administrator's browser submit a POST request.

   Therefore, **the management UI must not call `/api/v1/**` from the browser.**
   When the UI needs data already returned by the API, add a thin endpoint under
   `/admin/**` that calls the same service and returns the same DTO—this is how
   `/admin/sken/stav` and `/admin/kniznica/{id}/stream` work.

10. **Passwords and PINs use Argon2id**; they are never stored in plaintext and
    never displayed again. **The PIN is valid only for the REST API**—the management
    UI requires the full password. The Android client token is stored in the
    database as SHA-256; Argon2 is intentionally the wrong choice there because the
    token is verified on every request.

    `Argon2PasswordEncoder` requires **BouncyCastle**, which is not managed by the
    Boot BOM—the `bcprov-jdk18on` version is pinned in `pom.xml`. If it is missing,
    the failure appears only at runtime.

## Structure

```
backend/    Spring Boot server—REST API, Thymeleaf UI, SMB, indexing
frontend/   Android TV application (Kotlin)—currently empty
doc/        assignment and technology decisions
```

Thymeleaf templates belong in `backend/src/main/resources/templates`, **not** in
`frontend/`. The `frontend/` directory is reserved for the Android TV client.

The base package is `org.javerlabd.homecenter`. Packages are organized by
responsibility, not by layer:

| Package | Contents |
|---|---|
| `config` | `@ConfigurationProperties`, OpenAPI, MVC, and Spring Security configuration |
| `source` | Samba: connection (`SmbGateway`), source configuration, paths |
| `media` | media index—domain, repository, read service, extension classification |
| `metadata` | filename parser, TMDb client, index enrichment, and local poster cache |
| `scan` | Samba traversal and index maintenance, scan history |
| `stream` | Range logic, reading files from Samba into HTTP responses |
| `user` | accounts: domain, repository, roles, password and PIN hashing |
| `auth` | authentication: client tokens, `UserDetailsService`, Bearer filter |
| `api` | REST controllers and DTOs (JSON) |
| `admin` | Thymeleaf controllers and forms (HTML) |

`api` and `admin` are thin—both rely on the same services from `media`, `scan`,
and `source`. When logic is added, it belongs in a service, not a controller.

`auth` depends on `user`, never the other way around. When `user` needs to notify
the token side about something (for example, a password change requiring all TVs
to be logged out), it uses an event—`UserCredentialsChangedEvent`. A direct call
would create a circular dependency.

## UX rule

The target audience is an ordinary user with a remote control. The TV retains
three tiles—**Videos / Photos / Music**. Configuration (SMB sources, credentials,
user management, and starting a scan) belongs exclusively in the browser-based
Thymeleaf UI.

The PIN exists for the same reason: entering a password with a D-pad is awkward,
while four digits are manageable. The PIN therefore unlocks the TV, not server
management.
