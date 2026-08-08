# AGENTS.md

Pokyny pre AI agentov pracujúcich v tomto repozitári.

## O projekte

Domáce mediacentrum: Spring Boot server, ktorý indexuje médiá zo Samba úložiska
a streamuje ich do natívneho Android TV klienta. Server má popri REST API aj
webové management UI. Do budúcna sa počíta s rozšírením o smart asistenta pre
domácnosť — architektúra to má znášať, ale zatiaľ sa nerieši.

Dokumentácia je písaná po slovensky.

## Konvencia pre diagramy

Diagramy v dokumentácii kresli v **Mermaide** (` ```mermaid ` blok), nie ASCII
artom. Týka sa to README, `doc/` aj akýchkoľvek ďalších `.md` súborov.

## Stav a build

**Backend má funkčný skeleton** — index médií, sken Samby, filmové metadáta z TMDb,
REST API, streamovanie s Range requestami, Thymeleaf UI a prihlasovanie so správou
používateľov. **`frontend/` je stále prázdny**, Android TV klient sa ešte nezačal.

Prvé spustenie založí správcu **`admin` / `admin`** a vynúti zmenu hesla.

Build je **Maven** (nie Gradle) a vyžaduje **JDK 25**. Predvolené `JAVA_HOME`
na stroji ukazuje na staršiu Javu, treba ho prepnúť:

```powershell
$env:JAVA_HOME = "d:\java\jdk-25"
cd backend
```

| Príkaz | Čo robí |
|---|---|
| `mvn test` | všetky testy |
| `mvn "-Dtest=ByteRangeTest" test` | jeden testovací súbor |
| `mvn "-Dtest=ByteRangeTest#uzavretyRozsah" test` | jeden test |
| `mvn spring-boot:run` | spustí server na <http://localhost:8085/admin> |
| `mvn package` | spustiteľný jar do `target/` |

V PowerShelli musia byť `-D` parametre v úvodzovkách, inak si ich shell rozbije.

Server si pri štarte vytvorí `backend/data/homecenter.mv.db` (H2 index).
Priečinok `data/` je v `.gitignore` a pokojne sa dá zmazať — vznikne odznova,
prídeš len o index, ktorý sa doplní ďalším skenom.

V projekte je **Lombok**. Ak sa po zmene v pom.xml zrazu „stratia" gettery
alebo `log`, skontroluj `annotationProcessorPaths` v maven-compiler-plugine —
od JDK 23 sa procesory na classpath nehľadajú samy.

## Rozhodnutia, ktoré sú uzavreté

Neotváraj ich znova a neponúkaj alternatívy, pokiaľ o to používateľ výslovne
nepožiada:

| Vrstva | Voľba |
|---|---|
| Server | **Java 25 (LTS) + Spring Boot 4.1** |
| Management UI | **Thymeleaf + Bootstrap 5 + jQuery**, grafy cez **Chart.js** |
| Klient | **Android TV appka v Kotline** (Compose for TV, Media3) |
| Prihlasovanie | **Argon2id** na heslá aj PINy, **Bearer token** pre klienta |

Zvážené a **zamietnuté**: Python + FastAPI, Kotlin na serveri, postaviť projekt
na Jellyfine.

Odôvodnenie a zoznam knižníc: [doc/rozhodnutia.md](doc/rozhodnutia.md).
Ten súbor je zdroj pravdy — pri ďalších rozhodnutiach ho aktualizuj.

## Architektonické obmedzenia

Toto sú pravidlá, ktoré nie sú viditeľné z jedného súboru:

1. **Server je jediný držiteľ SMB credentials.** Klient nikdy nesiaha na Sambu
   priamo. Server súbory proxuje cez HTTP a **musí** podporovať Range requesty —
   bez nich nefunguje pretáčanie vo videu.

2. **Blokujúce IO je zámer, nie nedostatok.** smbj je blokujúce API a beží pod
   virtuálnymi vláknami (`spring.threads.virtual.enabled=true`). Neprepisuj
   streamovanie na reaktívny model ani nezavádzaj WebFlux — voľba Javy 25 stojí
   práve na tomto.

   Čítanie na pozíciu robí `com.hierynomus.smbj.share.File`:
   `read(byte[] buffer, long fileOffset, int bufferOffset, int length)`.
   `SmbRandomAccessFile` je trieda **jcifs-ng**, nie smbj — nehľadaj ju tu.

3. **Index-first.** Samba sa neskenuje pri obsluhe requestu. REST API číta
   z H2 indexu; sken beží na pozadí naplánovane a dá sa spustiť manuálne.
   Skenovanie v request ceste je regresia.

   **Zdrojov môže byť nastavených viac.** Nikdy nepredpokladaj „ten jeden“ —
   každá položka indexu vie svoj `source_id` a podľa neho sa aj streamuje.
   Sken ich prechádza **za sebou v jednej úlohe** a každý dostane vlastný riadok
   v `scan_run`; nedostupný zdroj sa označí `FAILED` a pokračuje sa ďalším.

4. **Žiadne transkódovanie, kým to konkrétny súbor nevynúti.** Východisko je
   direct play — server súbor len preposiela. FFmpeg sa nasadzuje až na
   preukázateľne nekompatibilný obsah.

   Náhľad v management UI to rešpektuje: čo prehliadač natívne nezvládne
   (`mkv`, `avi`, `wmv`, `heic`, `wma`), sa ani nezačne sťahovať — ukáže sa hláška
   a odkaz na stiahnutie. **Nezamieňaj to s tým, čo zvládne televízor** —
   Media3/ExoPlayer `mkv` aj `avi` prehrá bez problémov.

   Odpovede so súbormi tiež **nesmú mať `Cache-Control: no-store`**, ktoré Spring
   Security pridáva všade inde. Chrome stavia prehrávanie na multibufferi nad HTTP
   cache a bez cache mu prestane fungovať pretáčanie. Rieši to výnimka
   v `SecurityConfig` a vlastná hlavička v `MediaStreamResponse`.

5. **Thymeleaf a REST sú dve tenké vrstvy nad spoločnou service vrstvou.**
   Admin controllery vracajú HTML, API controllery JSON. Logika skenovania,
   indexácie a práce so Sambou patrí do služieb, nie do controllerov — inak sa
   začne duplikovať medzi UI a API.

6. **Kontrakt medzi serverom a klientom drží OpenAPI spec** (springdoc). Server
   je Java, klient Kotlin, modely sa nezdieľajú. Neprepisuj DTO ručne na oboch
   stranách.

7. **Schéma sa mení iba cez Flyway migrácie.** `V1__init.sql` nesie základnú
   štruktúru a dátový model a **už sa needituje** — každá ďalšia zmena je nový
   skript `V2__…`, `V3__…`. Prepísanie existujúcej migrácie rozbije checksum
   na každom nasadení, kde už raz zbehla.

8. **Frontend knižnice management UI sa servírujú lokálne cez WebJars.**
   Žiadne `<script src="https://cdn...">` — server beží v domácej sieti a UI
   musí fungovať bez internetu. Do management UI nezavádzaj build krok
   (npm, bundler) ani SPA framework; Thymeleaf renderuje HTML na serveri
   a jQuery ho dopĺňa.

9. **Prihlasovanie má dva oddelené filter chainy a nesmú sa zliať.**
   `/api/v1/**` je bezstavové, berie výhradne `Authorization: Bearer` a má
   vypnuté CSRF. Všetko ostatné je session s formulárom, CSRF a rolou `ADMIN`.
   Keby session platila aj na API, cudzia stránka by vedela prehliadaču
   prihláseného správcu podstrčiť POST.

   Preto **management UI nesmie volať `/api/v1/**` z prehliadača.** Keď treba
   v UI dáta, ktoré už API vracia, pridaj tenký endpoint pod `/admin/**`, ktorý
   volá tú istú službu a vracia to isté DTO — tak to robí `/admin/sken/stav`
   aj `/admin/kniznica/{id}/stream`.

10. **Heslá aj PINy idú cez Argon2id**, nikdy sa neukladajú otvorene a nikdy sa
    nezobrazujú späť. **PIN platí výhradne na REST API** — do management UI sa
    vyžaduje plné heslo. Token Android klienta je v databáze ako SHA-256; tam je
    Argon2 zámerne nesprávna voľba (overuje sa pri každom requeste).

    `Argon2PasswordEncoder` potrebuje **BouncyCastle**, ktorý Boot BOM nespravuje —
    verzia `bcprov-jdk18on` je pripnutá v `pom.xml`. Chýbajúca sa prejaví až za behu.

## Štruktúra

```
backend/    Spring Boot server — REST API, Thymeleaf UI, SMB, indexácia
frontend/   Android TV aplikácia (Kotlin) — zatiaľ prázdne
doc/        zadanie a technologické rozhodnutia
```

Thymeleaf šablóny patria do `backend/src/main/resources/templates`, **nie** do
`frontend/`. Priečinok `frontend/` je vyhradený pre Android TV klienta.

Základný package je `org.javerlabd.homecenter`. Balíky sú delené podľa
zodpovednosti, nie podľa vrstiev:

| Balík | Čo tam patrí |
|---|---|
| `config` | `@ConfigurationProperties`, OpenAPI, MVC a Spring Security konfigurácia |
| `source` | Samba: pripojenie (`SmbGateway`), nastavenie zdroja, cesty |
| `media` | index médií — doména, repository, čítacia service, klasifikácia prípon |
| `metadata` | parser názvov, TMDb klient, obohatenie indexu a lokálna cache plagátov |
| `scan` | prechod Samby a údržba indexu, história skenov |
| `stream` | Range logika, čítanie súboru zo Samby do HTTP odpovede |
| `user` | účty: doména, repository, roly, hashovanie hesiel a PINov |
| `auth` | prihlasovanie: tokeny klienta, `UserDetailsService`, Bearer filter |
| `api` | REST controllery a DTO (JSON) |
| `admin` | Thymeleaf controllery a formuláre (HTML) |

`api` a `admin` sú tenké — obe stoja nad rovnakými službami z `media`, `scan`
a `source`. Keď pribúda logika, patrí do služby, nie do controllera.

`auth` závisí na `user`, nikdy nie naopak. Keď potrebuje `user` niečo oznámiť
smerom k tokenom (napr. že sa zmenilo heslo a treba odhlásiť televízory), ide to
udalosťou — `UserCredentialsChangedEvent`. Priame volanie by uzavrelo kruh.

## UX pravidlo

Cieľová skupina je bežný používateľ s diaľkovým ovládačom. Na TV ostávajú tri
dlaždice — **Videá / Fotky / Hudba**. Konfigurácia (SMB zdroj, credentials,
správa používateľov, spustenie skenu) patrí výhradne do Thymeleaf UI v prehliadači.

Z toho istého dôvodu existuje PIN: heslo sa D-padom píše zle, štyri číslice sa
zvládnu. Preto PIN otvára televízor, nie správu servera.
